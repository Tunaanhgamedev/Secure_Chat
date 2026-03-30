package com.example.securechat.presentation.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechat.data.remote.WebRtcClient
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.webrtc.*
import javax.inject.Inject

enum class CallState { IDLE, CALLING, RINGING, CONNECTED, ENDED }

@HiltViewModel
class VideoCallViewModel @Inject constructor(
    private val webRtcClient: WebRtcClient,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val targetUserId: String = savedStateHandle.get<String>("userId") ?: ""
    val peerName: String     = savedStateHandle.get<String>("peerName") ?: ""
    val isIncoming: Boolean  = savedStateHandle.get<Boolean>("isIncoming") ?: false

    private val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val callId = if (myUid < targetUserId) "${myUid}_$targetUserId" else "${targetUserId}_$myUid"

    private val _callState = MutableStateFlow(if (isIncoming) CallState.RINGING else CallState.CALLING)
    val callState: StateFlow<CallState> = _callState

    private var peerConnection: PeerConnection? = null
    
    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack

    private val pcObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
            if (p0 == PeerConnection.IceConnectionState.CONNECTED) {
                _callState.value = CallState.CONNECTED
            } else if (p0 == PeerConnection.IceConnectionState.DISCONNECTED || p0 == PeerConnection.IceConnectionState.FAILED) {
                _callState.value = CallState.ENDED
            }
        }
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidate(candidate: IceCandidate) {
            webRtcClient.sendIceCandidate(callId, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex, !isIncoming)
        }
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onAddStream(p0: MediaStream?) {
            p0?.videoTracks?.firstOrNull()?.let { _remoteVideoTrack.value = it }
        }
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            receiver?.track()?.let { track ->
                if (track is VideoTrack) _remoteVideoTrack.value = track
            }
        }
    }

    init {
        initPeerConnection()
        if (isIncoming) {
            listenForOffer()
        } else {
            createOffer()
        }
        listenForIceCandidates()
    }

    private fun initPeerConnection() {
        peerConnection = webRtcClient.createPeerConnection(pcObserver)
        peerConnection?.addTrack(webRtcClient.getLocalVideoTrack())
        peerConnection?.addTrack(webRtcClient.getLocalAudioTrack())
    }

    private fun createOffer() {
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(this, sdp)
                webRtcClient.sendOffer(callId, sdp.description)
                listenForAnswer()
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())
    }

    private fun listenForOffer() {
        viewModelScope.launch {
            webRtcClient.listenForOffer(callId).filterNotNull().collectLatest { sdp ->
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() { createAnswer() }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, SessionDescription(SessionDescription.Type.OFFER, sdp))
            }
        }
    }

    private fun createAnswer() {
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(this, sdp)
                webRtcClient.sendAnswer(callId, sdp.description)
                _callState.value = CallState.CONNECTED
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())
    }

    private fun listenForAnswer() {
        viewModelScope.launch {
            webRtcClient.listenForAnswer(callId).filterNotNull().collectLatest { sdp ->
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() { _callState.value = CallState.CONNECTED }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
            }
        }
    }

    private fun listenForIceCandidates() {
        viewModelScope.launch {
            webRtcClient.listenForIceCandidates(callId, !isIncoming).collectLatest { model ->
                peerConnection?.addIceCandidate(IceCandidate(model.sdpMid, model.sdpMLineIndex, model.candidate))
            }
        }
    }

    fun endCall() {
        _callState.value = CallState.ENDED
        peerConnection?.close()
        webRtcClient.stopLocalVideo()
        // Here you should also update Firebase to tell the other side the call is over
    }

    override fun onCleared() {
        super.onCleared()
        peerConnection?.close()
        webRtcClient.stopLocalVideo()
    }
}
