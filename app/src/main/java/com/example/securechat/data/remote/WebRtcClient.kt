package com.example.securechat.data.remote

import android.content.Context
import org.webrtc.*
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class WebRtcClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val signalingClient: FirebaseSignalingClient
) {
    private val eglBase = EglBase.create()
    val eglBaseContext: EglBase.Context = eglBase.eglBaseContext

    private val peerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
        PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBaseContext, true, true))
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = false
            })
            .createPeerConnectionFactory()
    }

    private var videoCapturer: CameraVideoCapturer? = null
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val localAudioSource by lazy { peerConnectionFactory.createAudioSource(MediaConstraints()) }

    fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        return peerConnectionFactory.createPeerConnection(rtcConfig, observer)
    }

    // ─── Local Media Support ──────────────────────────────────────────────────
    fun startLocalVideo(surfaceViewRenderer: SurfaceViewRenderer) {
        surfaceViewRenderer.init(eglBaseContext, null)
        surfaceViewRenderer.setEnableHardwareScaler(true)
        surfaceViewRenderer.setMirror(true)

        videoCapturer = getVideoCapturer(context)
        videoCapturer?.initialize(SurfaceTextureHelper.create("CaptureThread", eglBaseContext), context, localVideoSource.capturerObserver)
        videoCapturer?.startCapture(720, 480, 30)

        val localVideoTrack = peerConnectionFactory.createVideoTrack("v_track", localVideoSource)
        localVideoTrack.addSink(surfaceViewRenderer)
    }

    fun getLocalVideoTrack(): VideoTrack = peerConnectionFactory.createVideoTrack("v_track", localVideoSource)
    fun getLocalAudioTrack(): AudioTrack = peerConnectionFactory.createAudioTrack("a_track", localAudioSource)

    private fun getVideoCapturer(context: Context): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return enumerator.createCapturer(deviceNames.firstOrNull(), null)
    }

    fun stopLocalVideo() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        videoCapturer = null
    }

    // ─── Signaling Proxy ──────────────────────────────────────────────────────
    fun sendOffer(callId: String, sdp: String) = signalingClient.sendOffer(callId, sdp)
    fun sendAnswer(callId: String, sdp: String) = signalingClient.sendAnswer(callId, sdp)
    fun sendIceCandidate(callId: String, candidate: String, sdpMid: String, sdpMLineIndex: Int, isCaller: Boolean) {
        signalingClient.sendIceCandidate(callId, candidate, sdpMid, sdpMLineIndex, isCaller)
    }
    fun listenForAnswer(callId: String) = signalingClient.listenForAnswer(callId)
    fun listenForOffer(callId: String)  = signalingClient.listenForOffer(callId)
    fun listenForIceCandidates(callId: String, isCaller: Boolean) = signalingClient.listenForIceCandidates(callId, isCaller)
}
