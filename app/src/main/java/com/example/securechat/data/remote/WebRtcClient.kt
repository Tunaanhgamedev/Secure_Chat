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
                .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                .createInitializationOptions()
        )
        
        // Use software decoding as the "bomb-proof" fallback for Xiaomi/4G
        // while aligning with the Options structure from the reference.
        val decoderFactory = SoftwareVideoDecoderFactory()
        val encoderFactory = SoftwareVideoEncoderFactory()
        
        PeerConnectionFactory.builder()
            .setVideoDecoderFactory(decoderFactory)
            .setVideoEncoderFactory(encoderFactory)
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = false
            })
            .createPeerConnectionFactory()
    }

    private var videoCapturer: CameraVideoCapturer? = null
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val localAudioSource by lazy { 
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        peerConnectionFactory.createAudioSource(constraints) 
    }
    
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null

    fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            // Add the TURN server from the reference repo for NAT traversal
            PeerConnection.IceServer.builder("turn:a.relay.metered.ca:443?transport=tcp")
                .setUsername("83eebabf8b4cce9d5dbcb649")
                .setPassword("2D7JvfkOQtBdYW3R").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            
            // Aggressive bundling to save ports and speed up connection on 4G
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
        return peerConnectionFactory.createPeerConnection(rtcConfig, observer)
    }

    fun getOfferConstraints(): MediaConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
    }

    // ─── Local Media Support ──────────────────────────────────────────────────
    private var audioManager: android.media.AudioManager? = null

    fun startLocalCapture() {
        if (videoCapturer == null) {
            videoCapturer = getVideoCapturer(context)
            val helper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
            videoCapturer?.initialize(helper, context, localVideoSource.capturerObserver)
            videoCapturer?.startCapture(640, 480, 30) // Use stable 480p resolution like reference
        }
        
        // Audio Management (Professional routing)
        if (audioManager == null) {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager?.apply {
                mode = android.media.AudioManager.MODE_IN_COMMUNICATION
                isSpeakerphoneOn = true // Default to speaker for video calls
                
                // Request audio focus for the call
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val focusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                        .build()
                    requestAudioFocus(focusRequest)
                } else {
                    requestAudioFocus(null, android.media.AudioManager.STREAM_VOICE_CALL, android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                }
            }
        }
    }

    fun getLocalVideoTrack(): VideoTrack {
        if (localVideoTrack == null) {
            localVideoTrack = peerConnectionFactory.createVideoTrack("v_track", localVideoSource)
            localVideoTrack?.setEnabled(true)
        }
        return localVideoTrack!!
    }

    fun getLocalAudioTrack(): AudioTrack {
        if (localAudioTrack == null) {
            localAudioTrack = peerConnectionFactory.createAudioTrack("a_track", localAudioSource)
            localAudioTrack?.setEnabled(true)
        }
        return localAudioTrack!!
    }

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
        localVideoTrack = null
        localAudioTrack = null
        
        // Reset Audio
        audioManager?.mode = android.media.AudioManager.MODE_NORMAL
        audioManager?.isSpeakerphoneOn = false
        audioManager = null
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
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

    fun createLocalStream(streamId: String): MediaStream {
        return peerConnectionFactory.createLocalMediaStream(streamId)
    }

    companion object {
        const val STREAM_ID = "ARDAMS"
    }
}
