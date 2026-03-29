package com.example.securechat.data.remote

import android.content.Context
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class WebRtcClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val signalingClient: FirebaseSignalingClient
) {
    val eglBaseContext: EglBase.Context = EglBase.create().eglBaseContext

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
    }

    private val peerConnectionFactory by lazy {
        PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        // Set up real connection semantics
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        return peerConnectionFactory.createPeerConnection(rtcConfig, observer)
    }

    fun sendOffer(callId: String, sdp: String) {
        signalingClient.sendOffer(callId, sdp)
    }

    fun sendAnswer(callId: String, sdp: String) {
        signalingClient.sendAnswer(callId, sdp)
    }

    fun sendIceCandidate(callId: String, candidate: String, sdpMid: String, sdpMLineIndex: Int, isCaller: Boolean) {
        signalingClient.sendIceCandidate(callId, candidate, sdpMid, sdpMLineIndex, isCaller)
    }

    fun listenForAnswer(callId: String) = signalingClient.listenForAnswer(callId)
    
    fun listenForOffer(callId: String) = signalingClient.listenForOffer(callId)
    
    fun listenForIceCandidates(callId: String, isCaller: Boolean) = signalingClient.listenForIceCandidates(callId, isCaller)
}
