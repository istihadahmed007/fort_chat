package com.fort.messenger.model

enum class CallType {
    AUDIO,
    VIDEO
}

enum class CallStatus {
    IDLE,
    OUTGOING_RINGING,
    INCOMING_RINGING,
    CONNECTING,
    CONNECTED,
    ENDED,
    DECLINED,
    BUSY,
    MISSED,
    FAILED,
    PERMISSION_DENIED
}

data class CallSession(
    val callId: String,
    val peerUserId: String,
    val peerDisplayName: String,
    val peerAvatarEmoji: String = "🛡️",
    val isCaller: Boolean,
    val callType: CallType,
    val status: CallStatus = CallStatus.IDLE,
    val durationSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isVideoEnabled: Boolean = true,
    val isFrontCamera: Boolean = true,
    val errorMessage: String? = null
)

data class RtcIceCandidateRecord(
    val candidate: String,
    val sdpMid: String,
    val sdpMLineIndex: Int,
    val serverUrl: String = ""
)

data class TurnServerConfig(
    val urls: List<String>,
    val username: String,
    val credential: String
)

data class RtcSessionDescriptionRecord(
    val type: String, // "offer" or "answer"
    val sdp: String
)

data class RemoteCallRecord(
    val callId: String,
    val callerUserId: String,
    val callerDisplayName: String,
    val receiverUserId: String,
    val callType: String, // "AUDIO" or "VIDEO"
    val status: String, // "RINGING", "ACCEPTED", "DECLINED", "ENDED", "BUSY"
    val offerSdp: String? = null,
    val answerSdp: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
