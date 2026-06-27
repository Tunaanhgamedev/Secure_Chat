package com.example.securechat.data.remote

import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.webrtc.*
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * WebRTC client following the reference repo pattern (codewithkael/FirebaseWebRTCVideoCall).
 *
 * Key design decisions (matching ref repo):
 * - PeerConnectionFactory is built once and reused
 * - Camera/audio objects are created fresh on each call via prepareCall()
 * - NO lazy properties for media sources so we can reset them cleanly
 * - Single addStream() call (no double addTrack + addStream)
 */
@Singleton
class WebRtcClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val signalingClient: FirebaseSignalingClient,
    private val firebaseAuth: com.google.firebase.auth.FirebaseAuth
) {
    private val TAG = "WebRtcClient"

    companion object {
        @Volatile private var initialized = false
    }

    // ── EglBase (shared, lives forever) ───────────────────────────────────────
    private val eglBase = EglBase.create()
    val eglBaseContext: EglBase.Context = eglBase.eglBaseContext

    init {
        firebaseAuth.addAuthStateListener { auth ->
            if (auth.currentUser == null) {
                Log.d(TAG, "User logged out, auto-closing WebRTC connection")
                closeConnection()
            }
        }
    }

    // ── PeerConnectionFactory (built once, never disposed) ─────────────────────
    private val peerConnectionFactory: PeerConnectionFactory by lazy {
        // Initialize exactly once per process. WebRTC aborts if called twice.
        if (!initialized) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(true)
                    .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                    .createInitializationOptions()
            )
            initialized = true
        }
        PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    // ── Per-call state (reset on closeConnection) ───────────────────────────
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var localVideoSource: VideoSource? = null
    private var localAudioSource: AudioSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var localStream: MediaStream? = null
    private var audioManager: AudioManager? = null
    private var currentCallId: String? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrackFlow: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    // ── ICE Servers ────────────────────────────────────────────────────────────
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer()
    )

    // ── Surface Renderers ──────────────────────────────────────────────────────
    fun initSurfaceView(view: SurfaceViewRenderer) {
        try {
            view.run {
                setEnableHardwareScaler(true)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                init(eglBaseContext, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "initSurfaceView failed", e)
        }
    }

    fun setLocalVideoSink(view: SurfaceViewRenderer) {
        localVideoTrack?.addSink(view)
    }

    // ── Call Setup ─────────────────────────────────────────────────────────────
    /**
     * Prepares camera/mic and creates PeerConnection.
     * Must be called before call() or answer().
     * Safe to call multiple times — will skip if already prepared.
     */
    fun prepareCall(observer: PeerConnection.Observer) {
        try {
            // Ensure any old state is cleaned up first
            closeConnection()
            
            startLocalMedia()
            
            // Fix Unified Plan crash: Use RTCConfiguration and addTrack instead of addStream
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            }
            
            peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, observer)
            
            // Add tracks to peerConnection
            localVideoTrack?.let { 
                peerConnection?.addTrack(it, listOf("local_stream")) 
            }
            localAudioTrack?.let { 
                peerConnection?.addTrack(it, listOf("local_stream")) 
            }
            
            Log.d(TAG, "prepareCall() complete with Unified Plan")
        } catch (e: Exception) {
            Log.e(TAG, "prepareCall() failed", e)
        }
    }

    private fun startLocalMedia() {
        // Video
        localVideoSource = peerConnectionFactory.createVideoSource(false)
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
        videoCapturer = getFrontCameraCapturer()
        videoCapturer?.initialize(surfaceTextureHelper, context, localVideoSource!!.capturerObserver)
        videoCapturer?.startCapture(480, 640, 30)

        val track = peerConnectionFactory.createVideoTrack("video_track_${System.currentTimeMillis()}", localVideoSource!!)
        track.setEnabled(true)
        localVideoTrack = track
        _localVideoTrack.value = track

        // Audio
        localAudioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory.createAudioTrack("audio_track_${System.currentTimeMillis()}", localAudioSource!!)
        localAudioTrack?.setEnabled(true)

        // Stream
        localStream = peerConnectionFactory.createLocalMediaStream("local_stream")
        localStream?.addTrack(localVideoTrack)
        localStream?.addTrack(localAudioTrack)

        // Audio routing
        try {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isSpeakerphoneOn = true
        } catch (e: Exception) {
            Log.e(TAG, "Audio routing failed", e)
        }
    }

    private fun getFrontCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        for (device in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(device)) {
                return enumerator.createCapturer(device, null)
            }
        }
        return enumerator.deviceNames.firstOrNull()?.let {
            enumerator.createCapturer(it, null)
        }
    }

    // ── Offer / Answer ─────────────────────────────────────────────────────────
    fun call(callId: String) {
        this.currentCallId = callId
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        peerConnection?.createOffer(object : MySdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp ?: return
                peerConnection?.setLocalDescription(object : MySdpObserver() {
                    override fun onSetSuccess() {
                        signalingClient.sendOffer(callId, sdp.description)
                        Log.d(TAG, "Offer sent for $callId")
                    }
                }, sdp)
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "createOffer failed: $error")
            }
        }, constraints)
    }

    fun answer(callId: String) {
        this.currentCallId = callId
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        peerConnection?.createAnswer(object : MySdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp ?: return
                peerConnection?.setLocalDescription(object : MySdpObserver() {
                    override fun onSetSuccess() {
                        signalingClient.sendAnswer(callId, sdp.description)
                        Log.d(TAG, "Answer sent for $callId")
                    }
                }, sdp)
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "createAnswer failed: $error")
            }
        }, constraints)
    }

    fun onRemoteSessionReceived(sdp: SessionDescription, onSetSuccess: () -> Unit = {}) {
        peerConnection?.setRemoteDescription(object : MySdpObserver() {
            override fun onSetSuccess() {
                Log.d(TAG, "onRemoteSessionReceived success")
                onSetSuccess()
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "onRemoteSessionReceived failure: $error")
            }
        }, sdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    // ── Controls ───────────────────────────────────────────────────────────────
    fun switchCamera() { videoCapturer?.switchCamera(null) }
    fun toggleAudio(enabled: Boolean) { localAudioTrack?.setEnabled(enabled) }
    fun toggleVideo(enabled: Boolean) { localVideoTrack?.setEnabled(enabled) }

    // ── Cleanup ────────────────────────────────────────────────────────────────
    fun closeConnection() {
        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) { Log.e(TAG, "stopCapture error", e) }
        videoCapturer?.dispose()
        videoCapturer = null

        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        localVideoTrack?.dispose()
        localVideoTrack = null
        _localVideoTrack.value = null
        localAudioTrack?.dispose()
        localAudioTrack = null

        localVideoSource?.dispose()
        localVideoSource = null
        localAudioSource?.dispose()
        localAudioSource = null

        localStream = null

        peerConnection?.close()
        peerConnection = null
        
        Log.d(TAG, "closeConnection() done")
        currentCallId?.let { 
            signalingClient.clearSignaling(it) 
            currentCallId = null
        }

        try {
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = false
        } catch (e: Exception) { }
        audioManager = null
    }

    // ── Signaling Proxy ────────────────────────────────────────────────────────
    fun sendIceCandidate(callId: String, candidate: String, sdpMid: String, sdpMLineIndex: Int, isCaller: Boolean) {
        signalingClient.sendIceCandidate(callId, candidate, sdpMid, sdpMLineIndex, isCaller)
    }
    fun listenForAnswer(callId: String) = signalingClient.listenForAnswer(callId)
    fun listenForOffer(callId: String)  = signalingClient.listenForOffer(callId)
    fun listenForIceCandidates(callId: String, isCaller: Boolean) = signalingClient.listenForIceCandidates(callId, isCaller)

    fun getLocalVideoTrack(): VideoTrack? = localVideoTrack
}

// Blank SdpObserver — from reference repo's MySdpObserver
open class MySdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}
