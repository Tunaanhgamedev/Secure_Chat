package com.example.securechat.presentation.chat

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechat.data.remote.WebRtcClient
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.webrtc.*
import javax.inject.Inject

enum class CallState { IDLE, CALLING, RINGING, CONNECTED, ENDED }

@HiltViewModel
class VideoCallViewModel @Inject constructor(
    private val webRtcClient: WebRtcClient,
    private val chatRepository: com.example.securechat.domain.repository.ChatRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val targetUserId: String = savedStateHandle.get<String>("userId") ?: ""
    val peerName: String     = savedStateHandle.get<String>("peerName") ?: ""
    val isIncoming: Boolean  = savedStateHandle.get<Boolean>("isIncoming") ?: false

    val eglContext: EglBase.Context = webRtcClient.eglBaseContext

    private val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val callId = if (myUid < targetUserId) "${myUid}_$targetUserId" else "${targetUserId}_$myUid"

    private val _callState = MutableStateFlow(if (isIncoming) CallState.RINGING else CallState.CALLING)
    val callState: StateFlow<CallState> = _callState

    private var peerConnection: PeerConnection? = null
    
    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack

    private val _networkMessage = MutableStateFlow<String?>(null)
    val networkMessage: StateFlow<String?> = _networkMessage

    // Audio signaling
    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null

    private val pcObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        _callState.value = CallState.CONNECTED
                        _networkMessage.value = null
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        _networkMessage.value = "Mạng yếu, đang thử lại..."
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        _networkMessage.value = "Kết nối thất bại"
                        endCall()
                    }
                    PeerConnection.IceConnectionState.CHECKING -> {
                        _networkMessage.value = "Đang kết nối..."
                    }
                    else -> {}
                }
            }
        }
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidate(candidate: IceCandidate) {
            webRtcClient.sendIceCandidate(callId, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex, !isIncoming)
        }
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onAddStream(p0: MediaStream?) {
            // Legacy callback — dispatch to main thread
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                p0?.videoTracks?.firstOrNull()?.let { track ->
                    track.setEnabled(true)
                    _remoteVideoTrack.value = track
                }
            }
        }
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            // UNIFIED_PLAN callback — dispatch to main thread
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                receiver?.track()?.let { track ->
                    track.setEnabled(true)
                    if (track is VideoTrack) _remoteVideoTrack.value = track
                }
            }
        }
    }

    private var isRemoteDescriptionSet = false
    private val iceCandidateBuffer = mutableListOf<IceCandidate>()
    private var offerCreated = false  // guard: ensure createOffer runs exactly once

    init {
        viewModelScope.launch {
            try {
                webRtcClient.startLocalCapture()
                initPeerConnection()

                // Start Audio Feedback
                if (isIncoming) {
                    playRingtone()
                } else {
                    playDialingTone()
                }

                if (isIncoming) {
                    // NOTE: respondToCall("accepted") was already called by CallManagerViewModel.
                    // Do NOT call it again here. Just listen for the offer from the caller.
                    listenForOffer()
                } else {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    val photoUrl = currentUser?.photoUrl?.toString()
                    val myName = currentUser?.displayName
                        ?: currentUser?.email?.substringBefore('@')
                        ?: "Unknown"
                    chatRepository.startCall(targetUserId, myName, photoUrl)
                    // Listen for receiver's "accepted" — create offer exactly once
                    chatRepository.listenForCallStatus(targetUserId).collect { status ->
                        if (!offerCreated && status == "accepted") {
                            offerCreated = true
                            createOffer()
                        }
                    }
                }
            } catch (e: Exception) {
                _networkMessage.value = "Lỗi khởi tạo: ${e.message}"
            }
        }

        // Both sides: watch for call termination signals
        viewModelScope.launch {
            chatRepository.listenForCallStatus(targetUserId).collect { status ->
                if (status == "ended" || status == "declined") {
                    endCall()
                }
            }
        }

        listenForIceCandidates()
    }

    private fun initPeerConnection() {
        try {
            peerConnection = webRtcClient.createPeerConnection(pcObserver)
            
            // Following reference repo: Create a local stream and add tracks to it
            val localStream = webRtcClient.createLocalStream(WebRtcClient.STREAM_ID)
            
            val videoTrack = webRtcClient.getLocalVideoTrack()
            videoTrack.setEnabled(true)
            localStream.addTrack(videoTrack)
            
            val audioTrack = webRtcClient.getLocalAudioTrack()
            audioTrack.setEnabled(true)
            localStream.addTrack(audioTrack)

            // Add the whole stream for maximum compatibility
            peerConnection?.addStream(localStream)
            
            // Also use addTrack for modern Unified Plan support
            peerConnection?.addTrack(videoTrack, listOf(WebRtcClient.STREAM_ID))
            peerConnection?.addTrack(audioTrack, listOf(WebRtcClient.STREAM_ID))
        } catch (e: Exception) {
            _networkMessage.value = "Lỗi khởi tạo cuộc gọi: ${e.message}"
        }
    }

    private fun createOffer() {
        val constraints = webRtcClient.getOfferConstraints()
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                val mangledSdp = SessionDescription(sdp.type, preferCodec(sdp.description, "VP8"))
                peerConnection?.setLocalDescription(this, mangledSdp)
                webRtcClient.sendOffer(callId, mangledSdp.description)
                listenForAnswer()
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    private fun listenForOffer() {
        viewModelScope.launch {
            webRtcClient.listenForOffer(callId).filterNotNull().collectLatest { sdp ->
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() { 
                        isRemoteDescriptionSet = true
                        drainIceBuffer()
                        createAnswer() 
                    }
                    override fun onSetFailure(p0: String?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, SessionDescription(SessionDescription.Type.OFFER, sdp))
            }
        }
    }

    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        peerConnection?.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    val mangledSdp = SessionDescription(sdp.type, preferCodec(sdp.description, "VP8"))
                    peerConnection?.setLocalDescription(this, mangledSdp)
                    webRtcClient.sendAnswer(callId, mangledSdp.description)
                    // Transition to CONNECTED immediately after signaling success.
                    // This moves the UI from RINGING to the Video screen.
                    stopSounds()
                    _callState.value = CallState.CONNECTED
                }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    private fun listenForAnswer() {
        viewModelScope.launch {
            webRtcClient.listenForAnswer(callId).filterNotNull().collectLatest { sdp ->
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() { 
                        isRemoteDescriptionSet = true
                        drainIceBuffer()
                        // Caller side: Transition to CONNECTED immediately after answer is received.
                        stopSounds()
                        _callState.value = CallState.CONNECTED
                    }
                    override fun onSetFailure(p0: String?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
            }
        }
    }

    private fun listenForIceCandidates() {
        viewModelScope.launch {
            webRtcClient.listenForIceCandidates(callId, !isIncoming).collectLatest { model ->
                val candidate = IceCandidate(model.sdpMid, model.sdpMLineIndex, model.candidate)
                if (isRemoteDescriptionSet) {
                    peerConnection?.addIceCandidate(candidate)
                } else {
                    iceCandidateBuffer.add(candidate)
                }
            }
        }
    }

    private fun drainIceBuffer() {
        iceCandidateBuffer.forEach { peerConnection?.addIceCandidate(it) }
        iceCandidateBuffer.clear()
    }

    fun endCall() {
        _callState.value = CallState.ENDED
        peerConnection?.close()
        webRtcClient.stopLocalVideo()
        viewModelScope.launch {
            if (isIncoming) {
                // I am receiver, I end the call by declining/ending it to the caller
                chatRepository.respondToCall(targetUserId, "ended")
            } else {
                // I am caller, I end the call for the receiver
                chatRepository.endCallSignal(targetUserId)
            }
        }
    }

    fun switchCamera() {
        webRtcClient.switchCamera()
    }

    fun getLocalVideoTrack(): VideoTrack = webRtcClient.getLocalVideoTrack()

    private fun playRingtone() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (notification != null) {
                mediaPlayer = MediaPlayer.create(context, notification)
                mediaPlayer?.isLooping = true
                mediaPlayer?.start()
            } else {
                playRingtoneSound() // Fallback to system tone if URI is missing
            }
        } catch (e: Exception) {
            // Fallback to tone if media player fails (safeguard against crash)
            playRingtoneSound()
        }
    }

    private fun playDialingTone() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 100)
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_DIAL, -1)
        } catch (e: Exception) {}
    }

    private fun playRingtoneSound() {
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_RING, 100)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, -1)
        } catch (e: Exception) {}
    }

    private fun stopSounds() {
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
            toneGenerator = null

            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {}
    }

    private fun preferCodec(sdp: String, codec: String): String {
        val lines = sdp.split("\r\n")
        val mLineIndex = lines.indexOfFirst { it.startsWith("m=video") }
        if (mLineIndex == -1) return sdp

        val mLine = lines[mLineIndex]
        val parts = mLine.split(" ").toMutableList()
        if (parts.size < 4) return sdp

        // Find the payload type of the target codec (e.g., VP8)
        val codecLines = lines.filter { it.contains("a=rtpmap") && it.contains(codec, ignoreCase = true) }
        val payloads = codecLines.mapNotNull { line ->
            val match = "a=rtpmap:(\\d+)".toRegex().find(line)
            match?.groupValues?.get(1)
        }

        if (payloads.isEmpty()) return sdp

        // Remove the targets from the list and re-insert them at the front of the codec list
        val remainingParts = parts.subList(3, parts.size).toMutableList()
        payloads.forEach { payload ->
            remainingParts.remove(payload)
        }
        val reorderedParts = parts.subList(0, 3) + payloads + remainingParts
        
        val newMLine = reorderedParts.joinToString(" ")
        val newLines = lines.toMutableList()
        newLines[mLineIndex] = newMLine
        return newLines.joinToString("\r\n")
    }

    override fun onCleared() {
        super.onCleared()
        peerConnection?.close()
        webRtcClient.stopLocalVideo()
    }
}
