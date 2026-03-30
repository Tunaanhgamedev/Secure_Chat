package com.example.securechat.data.remote

import android.content.Context
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory

class WebRtcClient(
    context: Context
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
        // Without an RTC configuration and full backend, we return a mock-capable connection
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        return peerConnectionFactory.createPeerConnection(rtcConfig, observer)
    }
}
