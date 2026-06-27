package com.example.securechat.presentation.chat

import android.content.Context
import android.util.Log
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

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack

    private val _localVideoTrack = webRtcClient.localVideoTrackFlow
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack

    private val _networkMessage = MutableStateFlow<String?>(null)
    val networkMessage: StateFlow<String?> = _networkMessage

    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null

    private var isRemoteDescriptionSet = false
    private val iceCandidateBuffer = mutableListOf<IceCandidate>()
    private var offerCreated = false

    // PeerConnection.Observer — follows reference repo's MyPeerObserver pattern
    private val pcObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        _callState.value = CallState.CONNECTED
                        _networkMessage.value = null
                        stopSounds()
                        Log.d("VideoCallVM", "ICE Connected/Completed")
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        _networkMessage.value = "Mạng yếu, đang thử lại..."
                        Log.d("VideoCallVM", "ICE Disconnected")
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        _networkMessage.value = "Kết nối thất bại"
                        Log.e("VideoCallVM", "ICE Failed")
                        endCall()
                    }
                    PeerConnection.IceConnectionState.CHECKING -> {
                        _networkMessage.value = "Đang bắt tay kết nối..."
                        Log.d("VideoCallVM", "ICE Checking")
                    }
                    else -> Log.d("VideoCallVM", "ICE State: $state")
                }
            }
        }
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidate(candidate: IceCandidate) {
            Log.d("VideoCallVM", "New local ICE Candidate: ${candidate.sdpMid}")
            webRtcClient.sendIceCandidate(callId, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex, !isIncoming)
        }
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        // Reference repo uses onAddStream for stream video, we accept both callbacks
        override fun onAddStream(stream: MediaStream?) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                stream?.videoTracks?.firstOrNull()?.let { track ->
                    track.setEnabled(true)
                    _remoteVideoTrack.value = track
                }
            }
        }
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            val track = receiver?.track()
            Log.d("VideoCallVM", "onAddTrack: type=${track?.kind()}, id=${track?.id()}")
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                track?.let { t ->
                    if (t is VideoTrack) {
                        Log.d("VideoCallVM", "Remote Video Track ENABLED")
                        t.setEnabled(true)
                        _remoteVideoTrack.value = t
                    } else if (t is AudioTrack) {
                        Log.d("VideoCallVM", "Remote Audio Track enabled")
                        t.setEnabled(true)
                    }
                }
            }
        }
    }

    init {
        // Setup local camera + mic + peer connection (reference repo: prepareClient)
        webRtcClient.prepareCall(pcObserver)

        if (isIncoming) {
            playRingtone()
            listenForOffer()
        } else {
            playDialingTone()
            viewModelScope.launch {
                try {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    val photoUrl = currentUser?.photoUrl?.toString()
                    val myName = currentUser?.displayName
                        ?: currentUser?.email?.substringBefore('@')
                        ?: "Unknown"
                    chatRepository.startCall(targetUserId, myName, photoUrl)
                    // Wait for receiver to accept, then create offer
                    chatRepository.listenForCallStatus(targetUserId).collect { status ->
                        if (!offerCreated && status == "accepted") {
                            offerCreated = true
                            webRtcClient.call(callId)
                            listenForAnswer()
                        }
                    }
                } catch (e: Exception) {
                    _networkMessage.value = "Lỗi khởi tạo: ${e.message}"
                }
            }
        }

        // Both sides: watch for call termination
        viewModelScope.launch {
            chatRepository.listenForCallStatus(targetUserId).collect { status ->
                // If status is null, it means the other side removed the node (call ended)
                // For the caller, the node starts at null, so we only end if already connected or if we are the receiver (caller cancelled)
                if (status == "ended" || status == "declined") {
                    endCall()
                } else if (status == null) {
                    if (isIncoming || _callState.value == CallState.CONNECTED) {
                        endCall()
                    }
                }
            }
        }

        listenForIceCandidates()
    }

    private fun listenForOffer() {
        viewModelScope.launch {
            webRtcClient.listenForOffer(callId).filterNotNull().collectLatest { sdp ->
                webRtcClient.onRemoteSessionReceived(
                    SessionDescription(SessionDescription.Type.OFFER, sdp)
                ) {
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        isRemoteDescriptionSet = true
                        drainIceBuffer()
                        webRtcClient.answer(callId)
                        stopSounds()
                        _callState.value = CallState.CONNECTED
                    }
                }
            }
        }
    }

    private fun listenForAnswer() {
        viewModelScope.launch {
            webRtcClient.listenForAnswer(callId).filterNotNull().collectLatest { sdp ->
                webRtcClient.onRemoteSessionReceived(
                    SessionDescription(SessionDescription.Type.ANSWER, sdp)
                ) {
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        isRemoteDescriptionSet = true
                        drainIceBuffer()
                        stopSounds()
                        _callState.value = CallState.CONNECTED
                    }
                }
            }
        }
    }

    private fun listenForIceCandidates() {
        viewModelScope.launch {
            webRtcClient.listenForIceCandidates(callId, !isIncoming).collectLatest { model ->
                val candidate = IceCandidate(model.sdpMid, model.sdpMLineIndex, model.candidate)
                if (isRemoteDescriptionSet) {
                    webRtcClient.addIceCandidate(candidate)
                } else {
                    iceCandidateBuffer.add(candidate)
                }
            }
        }
    }

    private fun drainIceBuffer() {
        iceCandidateBuffer.forEach { webRtcClient.addIceCandidate(it) }
        iceCandidateBuffer.clear()
    }

    fun endCall() {
        if (_callState.value == CallState.ENDED) return
        _callState.value = CallState.ENDED
        stopSounds()
        webRtcClient.closeConnection()
        viewModelScope.launch {
            if (isIncoming) {
                chatRepository.respondToCall(targetUserId, "ended")
            } else {
                chatRepository.endCallSignal(targetUserId)
            }
        }
    }

    fun switchCamera() = webRtcClient.switchCamera()

    fun toggleAudio(enabled: Boolean) = webRtcClient.toggleAudio(enabled)

    fun toggleVideo(enabled: Boolean) = webRtcClient.toggleVideo(enabled)

    fun initSurfaceView(view: org.webrtc.SurfaceViewRenderer) = webRtcClient.initSurfaceView(view)

    fun setLocalVideoSink(view: org.webrtc.SurfaceViewRenderer) = webRtcClient.setLocalVideoSink(view)

    private fun playRingtone() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (uri != null) {
                mediaPlayer = MediaPlayer.create(context, uri)
                mediaPlayer?.isLooping = true
                mediaPlayer?.start()
            } else {
                playTone()
            }
        } catch (e: Exception) {
            playTone()
        }
    }

    private fun playDialingTone() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 100)
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_DIAL, -1)
        } catch (e: Exception) {}
    }

    private fun playTone() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_RING, 100)
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

    override fun onCleared() {
        super.onCleared()
        webRtcClient.closeConnection()
    }
}
