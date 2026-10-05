package com.fort.messenger.webrtc

import android.content.Context
import android.media.AudioManager
import com.fort.messenger.model.CallSession
import com.fort.messenger.model.CallStatus
import com.fort.messenger.model.CallType
import com.fort.messenger.model.RtcIceCandidateRecord
import com.fort.messenger.model.TurnServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.*

class WebRtcCallManager(
    private val context: Context,
    private val onIceCandidateGenerated: (RtcIceCandidateRecord) -> Unit,
    private val onCallConnected: () -> Unit,
    private val onCallDisconnected: (String) -> Unit,
    private val turnServerConfigs: List<TurnServerConfig> = emptyList()
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrackInternal: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var eglBase: EglBase? = null
    val rootEglBase: EglBase? get() = eglBase

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private var isInitialized = false
    private var isSpeakerphoneOn = false
    private var isMuted = false

    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private val addedCandidateSignatures = mutableSetOf<String>()
    private var hasCreatedLocalAnswer = false
    private var hasAppliedRemoteAnswer = false

    /**
     * STUN fallback plus short-lived TURN credentials fetched from the authenticated backend.
     * The Coturn shared secret stays in Cloud Functions and is never shipped in the APK.
     */
    private val iceServers: List<PeerConnection.IceServer> by lazy {
        mutableListOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        ).apply {
            turnServerConfigs.forEach { config ->
                if (config.username.isNotBlank() && config.credential.isNotBlank()) {
                    config.urls
                        .filter { it.startsWith("turn:", ignoreCase = true) || it.startsWith("turns:", ignoreCase = true) }
                        .forEach { url ->
                            val builder = PeerConnection.IceServer.builder(url)
                            builder.setUsername(config.username)
                            builder.setPassword(config.credential)
                            add(builder.createIceServer())
                        }
                }
            }
        }
    }

    fun init() {
        if (isInitialized) return
        try {
            eglBase = EglBase.create()
            val initOptions = PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)

            val encoderFactory = DefaultVideoEncoderFactory(eglBase?.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(eglBase?.eglBaseContext)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .setOptions(PeerConnectionFactory.Options())
                .createPeerConnectionFactory()

            isInitialized = true
        } catch (_: UnsatisfiedLinkError) {
            // In Robolectric unit test environments where native WebRTC .so is not loaded
            isInitialized = false
        } catch (e: Exception) {
            isInitialized = false
        }
    }

    fun getEglBaseContext(): EglBase.Context? = eglBase?.eglBaseContext

    fun startLocalMedia(callType: CallType, onSurfaceRenderer: SurfaceViewRenderer? = null) {
        if (!isInitialized) init()
        val factory = peerConnectionFactory ?: return

        // 1. Audio Track (idempotent initialization)
        if (localAudioTrack == null) {
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            }
            audioSource = factory.createAudioSource(audioConstraints)
            localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource)
            localAudioTrack?.setEnabled(true)
        }

        // Set default audio routing
        setSpeakerphone(callType == CallType.VIDEO)

        // 2. Video Track (if video call, idempotent initialization)
        if (callType == CallType.VIDEO && localVideoTrackInternal == null) {
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase?.eglBaseContext)
            val capturer = createCameraCapturer()
            if (capturer != null) {
                videoCapturer = capturer
                videoSource = factory.createVideoSource(capturer.isScreencast)
                capturer.initialize(surfaceTextureHelper, appContext, videoSource?.capturerObserver)
                capturer.startCapture(1280, 720, 30)

                localVideoTrackInternal = factory.createVideoTrack("ARDAMSv0", videoSource)
                localVideoTrackInternal?.setEnabled(true)
                _localVideoTrack.value = localVideoTrackInternal
            }
        }

        if (onSurfaceRenderer != null && localVideoTrackInternal != null) {
            localVideoTrackInternal?.addSink(onSurfaceRenderer)
        }

        // If peerConnection is already initialized, attach only if not already attached
        peerConnection?.let { pc ->
            val existingTrackIds = pc.senders.mapNotNull { it.track()?.id() }.toSet()
            localAudioTrack?.let { if (it.id() !in existingTrackIds) pc.addTrack(it, listOf("ARDAMS")) }
            localVideoTrackInternal?.let { if (it.id() !in existingTrackIds) pc.addTrack(it, listOf("ARDAMS")) }
        }
    }

    fun createPeerConnection(
        remoteVideoSink: VideoSink? = null
    ): PeerConnection? {
        val factory = peerConnectionFactory ?: return null
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        onCallConnected()
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        onCallDisconnected("Network connection failed.")
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        onCallDisconnected("Peer disconnected.")
                    }
                    else -> {}
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    onIceCandidateGenerated(
                        RtcIceCandidateRecord(
                            candidate = candidate.sdp,
                            sdpMid = candidate.sdpMid ?: "",
                            sdpMLineIndex = candidate.sdpMLineIndex,
                            serverUrl = candidate.serverUrl ?: ""
                        )
                    )
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {}

            override fun onRemoveStream(stream: MediaStream?) {}

            override fun onDataChannel(channel: DataChannel?) {}

            override fun onRenegotiationNeeded() {}

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    _remoteVideoTrack.value = track
                    if (remoteVideoSink != null) {
                        track.addSink(remoteVideoSink)
                    }
                }
            }
        }

        val pc = factory.createPeerConnection(rtcConfig, observer) ?: return null

        // Add local tracks before any SDP offer or answer is generated
        localAudioTrack?.let { pc.addTrack(it, listOf("ARDAMS")) }
        localVideoTrackInternal?.let { pc.addTrack(it, listOf("ARDAMS")) }

        peerConnection = pc
        hasCreatedLocalAnswer = false
        hasAppliedRemoteAnswer = false
        return pc
    }

    fun createOffer(onOfferCreated: (SessionDescription) -> Unit) {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        pc.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    pc.setLocalDescription(SdpObserverAdapter(), desc)
                    onOfferCreated(desc)
                }
            }
        }, constraints)
    }

    fun createAnswer(remoteOfferSdp: String, onAnswerCreated: (SessionDescription) -> Unit) {
        if (hasCreatedLocalAnswer) return
        val pc = peerConnection ?: return
        hasCreatedLocalAnswer = true
        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, remoteOfferSdp)
        pc.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                drainPendingIceCandidates()
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }
                pc.createAnswer(object : SdpObserverAdapter() {
                    override fun onCreateSuccess(answerDesc: SessionDescription?) {
                        if (answerDesc != null) {
                            pc.setLocalDescription(SdpObserverAdapter(), answerDesc)
                            onAnswerCreated(answerDesc)
                        }
                    }
                }, constraints)
            }
        }, remoteDesc)
    }

    fun setRemoteAnswer(remoteAnswerSdp: String) {
        if (hasAppliedRemoteAnswer) return
        val pc = peerConnection ?: return
        hasAppliedRemoteAnswer = true
        val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, remoteAnswerSdp)
        pc.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                drainPendingIceCandidates()
            }
        }, remoteDesc)
    }

    fun addRemoteIceCandidate(candidate: RtcIceCandidateRecord) {
        val sig = "${candidate.sdpMid}_${candidate.sdpMLineIndex}_${candidate.candidate}"
        if (!addedCandidateSignatures.add(sig)) return

        val iceCandidate = IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.candidate)
        val pc = peerConnection
        if (pc != null && pc.remoteDescription != null) {
            pc.addIceCandidate(iceCandidate)
        } else {
            pendingIceCandidates.add(iceCandidate)
        }
    }

    private fun drainPendingIceCandidates() {
        val pc = peerConnection ?: return
        if (pc.remoteDescription == null) return
        val toDrain = ArrayList(pendingIceCandidates)
        pendingIceCandidates.clear()
        toDrain.forEach { pc.addIceCandidate(it) }
    }

    fun toggleMute(): Boolean {
        isMuted = !isMuted
        localAudioTrack?.setEnabled(!isMuted)
        return isMuted
    }

    fun toggleSpeakerphone(): Boolean {
        isSpeakerphoneOn = !isSpeakerphoneOn
        setSpeakerphone(isSpeakerphoneOn)
        return isSpeakerphoneOn
    }

    private fun setSpeakerphone(on: Boolean) {
        isSpeakerphoneOn = on
        audioManager?.let { am ->
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = on
        }
    }

    fun toggleVideo(enabled: Boolean) {
        localVideoTrackInternal?.setEnabled(enabled)
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(appContext)
        val deviceNames = enumerator.deviceNames

        // Prefer front camera first
        for (name in deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                val capturer = enumerator.createCapturer(name, null)
                if (capturer != null) return capturer
            }
        }
        for (name in deviceNames) {
            if (!enumerator.isFrontFacing(name)) {
                val capturer = enumerator.createCapturer(name, null)
                if (capturer != null) return capturer
            }
        }
        return null
    }

    fun close() {
        try {
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = false

            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null

            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            _localVideoTrack.value = null
            _remoteVideoTrack.value = null

            localVideoTrackInternal?.dispose()
            localVideoTrackInternal = null

            videoSource?.dispose()
            videoSource = null

            localAudioTrack?.dispose()
            localAudioTrack = null

            audioSource?.dispose()
            audioSource = null

            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null

            peerConnectionFactory?.dispose()
            peerConnectionFactory = null

            eglBase?.release()
            eglBase = null

            pendingIceCandidates.clear()
            addedCandidateSignatures.clear()
            hasCreatedLocalAnswer = false
            hasAppliedRemoteAnswer = false
        } catch (_: Exception) {}
        isInitialized = false
    }

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}
