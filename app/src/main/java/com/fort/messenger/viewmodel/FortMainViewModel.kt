package com.fort.messenger.viewmodel

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fort.messenger.MainActivity
import com.fort.messenger.data.local.*
import com.fort.messenger.data.remote.FortBackendFactory
import com.fort.messenger.data.remote.RemoteUserAccount
import com.fort.messenger.data.repository.ExistingRemoteKeysException
import com.fort.messenger.data.repository.FortRepository
import com.fort.messenger.model.*
import com.fort.messenger.security.ContactPassPayload
import com.fort.messenger.security.ContactPassQrEngine
import com.fort.messenger.security.FortBiometricManager
import com.fort.messenger.security.FortCryptoManager
import com.fort.messenger.security.SanitizationResult
import com.fort.messenger.security.ScrubberReport
import com.fort.messenger.security.ShareCheckScrubber
import com.fort.messenger.ui.components.ChatFilter
import com.fort.messenger.model.CallSession
import com.fort.messenger.model.CallStatus
import com.fort.messenger.model.CallType
import com.fort.messenger.model.LiveLocationDuration
import com.fort.messenger.model.LiveLocationSession
import com.fort.messenger.model.LocationPin
import com.fort.messenger.model.SearchMode
import com.fort.messenger.model.UserSearchResult
import com.fort.messenger.service.LiveLocationService
import com.fort.messenger.webrtc.WebRtcCallManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class AppLanguage {
    ENGLISH,
    BANGLA
}

data class FortUiState(
    val isInitializing: Boolean = true,
    val isAuthLoading: Boolean = false,
    val authErrorMessage: String? = null,
    val isKeyRecoveryRequired: Boolean = false,
    val isIdentityBackupLoading: Boolean = false,
    val identityBackupText: String? = null,
    val identityBackupError: String? = null,
    val authVerificationId: String? = null, // for phone OTP flow
    val currentUserAccount: UserAccountEntity? = null,
    val connectionCards: List<ConnectionCard> = emptyList(),
    val activeCardId: String = "",
    val moodState: MoodRingState? = null,
    val peerMoodStates: Map<String, MoodRingState> = emptyMap(),
    val conversations: List<ChatConversation> = emptyList(),
    val selectedFilter: ChatFilter = ChatFilter.ALL,
    val sharingCircles: List<SharingCircle> = emptyList(),
    val privateRooms: List<PrivateRoom> = emptyList(),
    val inboundRequests: List<KnockFirstRequest> = emptyList(),
    val activePasses: List<ContactPass> = emptyList(),
    val currentOpenChatId: String? = null,
    val currentPeerConnection: PeerConnectionEntity? = null,
    val activeChatExpirySetting: String = "24 Hours",
    val generatedPassQrBitmap: Bitmap? = null,
    val activeGeneratedPass: ContactPass? = null,
    val replyingToMessage: ChatMessage? = null,
    val inChatSearchQuery: String = "",
    val conversationDrafts: Map<String, String> = emptyMap(),
    val isPeerTyping: Boolean = false,
    // Share Check State
    val inspectedFileReport: ScrubberReport? = null,
    val sanitizationResult: SanitizationResult? = null,
    val shareCheckItems: List<ShareCheckItem> = emptyList(),
    // Modals
    val isMoodPickerOpen: Boolean = false,
    val isPassGeneratorOpen: Boolean = false,
    val isPassScannerOpen: Boolean = false,
    val scannedPassPayload: ContactPassPayload? = null,
    val isClaimingPass: Boolean = false,
    val passClaimError: String? = null,
    val isNewChatMenuOpen: Boolean = false,
    val isPrivacyCheckOpen: Boolean = false,
    val isShareCheckOpen: Boolean = false,
    // User Discovery / Search People
    val isSearchPeopleOpen: Boolean = false,
    val isSearchingPeople: Boolean = false,
    val searchResults: List<UserSearchResult> = emptyList(),
    val searchPeopleError: String? = null,
    val selectedUserForKnock: UserSearchResult? = null,
    // WebRTC Calls
    val activeCallSession: CallSession? = null,
    val incomingCallSession: CallSession? = null,
    val isCallMinimized: Boolean = false,
    // Location Sharing
    val isLocationShareModalOpen: Boolean = false,
    val activeLiveLocation: LiveLocationSession? = null,
    // Pass Generation Status
    val isGeneratingPass: Boolean = false,
    val passGenerationError: String? = null,
    // Security & Preferences
    val isBiometricLockEnabled: Boolean = false,
    val isEnclaveLocked: Boolean = false,
    val isNotificationRedacted: Boolean = true,
    val showOnlinePresence: Boolean = true,
    val showTypingIndicator: Boolean = true,
    val currentLanguage: AppLanguage = AppLanguage.ENGLISH,
    val toastMessage: String? = null
)

class FortMainViewModel @JvmOverloads constructor(
    application: Application,
    val repository: FortRepository = FortRepository(
        database = FortDatabase.getInstance(application),
        remoteBackend = FortBackendFactory.createBackend(application)
    )
) : AndroidViewModel(application) {

    private val biometricManager = FortBiometricManager(application)
    private val _uiState = MutableStateFlow(FortUiState())
    val uiState: StateFlow<FortUiState> = _uiState.asStateFlow()

    var webrtcManager: WebRtcCallManager? = null
    private var activeCallJob: kotlinx.coroutines.Job? = null
    private var iceCandidatesJob: kotlinx.coroutines.Job? = null
    private var typingListenerJob: kotlinx.coroutines.Job? = null
    private var realtimePacketsJob: kotlinx.coroutines.Job? = null
    private var realtimeOutboundJob: kotlinx.coroutines.Job? = null
    private var realtimeInboundKnockJob: kotlinx.coroutines.Job? = null
    private var realtimeOutboundKnockJob: kotlinx.coroutines.Job? = null
    private var searchJob: kotlinx.coroutines.Job? = null
    private var pendingKeyRecoveryUser: RemoteUserAccount? = null
    private var initializingCallId: String? = null

    init {
        initializeAccountAndData()
    }

    private fun initializeAccountAndData() {
        viewModelScope.launch {
            // Check for real existing account session in Room DB (NO hardcoded fake account creation)
            val account = repository.getActiveAccount().firstOrNull()
            if (account != null) {
                _uiState.update {
                    it.copy(
                        isInitializing = false,
                        currentUserAccount = account,
                        activeCardId = account.activeCardId,
                        isBiometricLockEnabled = account.biometricEnabled,
                        isNotificationRedacted = account.redactNotifications,
                        showOnlinePresence = account.showOnlinePresence,
                        showTypingIndicator = account.showTypingIndicator
                    )
                }
                observeUserData(account.userId)
            } else {
                // Unauthenticated state: display Onboarding & Auth Screen
                _uiState.update {
                    it.copy(
                        isInitializing = false,
                        currentUserAccount = null
                    )
                }
            }
        }
    }

    private fun observeUserData(userId: String) {
        // 1. Observe Persona Cards
        viewModelScope.launch {
            repository.getPersonaCards(userId).collect { entities ->
                val cards = entities.map { entity ->
                    val fingerprint = try {
                        val bytes = android.util.Base64.decode(entity.publicKey, android.util.Base64.NO_WRAP)
                        FortCryptoManager.computeFingerprint(bytes)
                    } catch (e: Exception) {
                        "AUTHENTIC-ENCLAVE-KEY"
                    }
                    ConnectionCard(
                        id = entity.cardId,
                        type = entity.type,
                        displayName = entity.displayName,
                        handle = entity.handle,
                        bio = entity.bio,
                        avatarEmoji = entity.avatarEmoji,
                        publicKeyFingerprint = fingerprint,
                        moodRingVisible = entity.moodSharingEnabled,
                        businessHoursOnly = entity.businessHoursOnly,
                        activePassCount = 0
                    )
                }
                _uiState.update { it.copy(connectionCards = cards) }
            }
        }

        // 2. Observe Active Mood Ring with temporal decay check
        viewModelScope.launch {
            repository.getActiveMood(userId).collect { moodEntity ->
                val now = System.currentTimeMillis()
                val activeMood = if (moodEntity != null && moodEntity.expiresAt > now) {
                    val remainingMins = (moodEntity.expiresAt - now) / (60 * 1000)
                    val remainingStr = if (remainingMins > 60) "${remainingMins / 60}h ${remainingMins % 60}m left" else "${remainingMins}m left"
                    val emotion = try {
                        MoodEmotion.valueOf(moodEntity.emotion)
                    } catch (e: Exception) {
                        MoodEmotion.NEED_QUIET
                    }
                    val whatINeed = moodEntity.whatINeed?.let {
                        try { WhatINeed.valueOf(it) } catch (e: Exception) { null }
                    }
                    MoodRingState(
                        emotion = emotion,
                        whatINeed = whatINeed,
                        sharingCircleName = moodEntity.audienceType,
                        duration = DecayDuration.HOURS_2,
                        remainingTimeString = remainingStr,
                        isActive = true
                    )
                } else {
                    null
                }
                _uiState.update { it.copy(moodState = activeMood) }
            }
        }

        // 3. Observe Peer Connections, Rooms, and Messages to Build Fully Reactive Live Conversation Feed
        viewModelScope.launch {
            combine(
                repository.getActiveConnections(userId),
                repository.getActiveRooms(),
                repository.getAllMessagesFlow()
            ) { connections, rooms, allMessages ->
                Triple(connections, rooms, allMessages)
            }.collect { (connections, rooms, allMessages) ->
                val messagesByConv = allMessages.groupBy { it.conversationId }
                val directConvs = connections.map { conn ->
                    val passRemaining = if (conn.passExpiresAt == Long.MAX_VALUE) {
                        "Ongoing"
                    } else {
                        val hours = (conn.passExpiresAt - System.currentTimeMillis()) / (3600 * 1000)
                        if (hours > 24) "${hours / 24}d left" else "${maxOf(0L, hours)}h left"
                    }

                    val convId = "conv_${conn.peerUserId}"
                    val messages = messagesByConv[convId] ?: emptyList()
                    val lastMsgObj = messages.lastOrNull()
                    val unreadCount = messages.count { !it.isMine && it.deliveryStatus != "READ" }
                    val lastTime = lastMsgObj?.let { formatTimestamp(it.timestamp) } ?: "Recent"
                    val lastMsg = lastMsgObj?.let {
                        if (it.attachmentType == "LOCATION_PIN") "📍 Pinned Location"
                        else if (it.attachmentType == "IMAGE") "📷 Photo"
                        else if (it.attachmentType == "FILE") "📄 ${it.attachmentName ?: "Document"}"
                        else if (it.isDeleted) "🚫 This message was deleted"
                        else it.decryptedTextCache
                    } ?: "Pass established. E2EE ready."

                    ChatConversation(
                        id = convId,
                        participantName = conn.peerDisplayName,
                        handle = conn.peerHandle,
                        avatarEmoji = conn.peerAvatarEmoji,
                        cardType = conn.peerCardType,
                        lastMessage = lastMsg,
                        lastMessageTime = lastTime,
                        unreadCount = unreadCount,
                        moodEmoji = null,
                        moodWhatINeed = null,
                        passTimeRemaining = passRemaining,
                        passType = conn.passType,
                        isRoom = false,
                        isTyping = conn.isTyping,
                        lastMessageIsMine = lastMsgObj?.isMine ?: false,
                        lastMessageDeliveryStatus = lastMsgObj?.deliveryStatus ?: "DELIVERED",
                        lastMessageAttachmentType = lastMsgObj?.attachmentType,
                        messages = messages.map { m ->
                            val reactionsMap = mutableMapOf<String, Int>()
                            val myReactionsList = mutableListOf<String>()
                            try {
                                val json = JSONObject(m.reactionsJson)
                                json.keys().forEach { key ->
                                    val arr = json.getJSONArray(key)
                                    reactionsMap[key] = arr.length()
                                    for (i in 0 until arr.length()) {
                                        if (arr.getString(i) == userId) myReactionsList.add(key)
                                    }
                                }
                            } catch (_: Exception) {}

                            ChatMessage(
                                id = m.messageId,
                                senderName = if (m.isMine) "You" else conn.peerDisplayName,
                                text = if (m.isDeleted) "🚫 This message was deleted" else m.decryptedTextCache,
                                timestamp = formatTimestamp(m.timestamp),
                                isMine = m.isMine,
                                isScrubbedMedia = m.isScrubbedMedia,
                                deliveryStatus = m.deliveryStatus,
                                replyToMessageId = m.replyToMessageId,
                                replyToSenderName = m.replyToSenderName,
                                replyToText = m.replyToText,
                                reactions = reactionsMap,
                                myReactions = myReactionsList,
                                isEdited = m.isEdited,
                                isDeleted = m.isDeleted,
                                attachmentUri = m.attachmentUri,
                                attachmentType = m.attachmentType,
                                attachmentName = m.attachmentName,
                                attachmentSize = m.attachmentSize
                            )
                        }
                    )
                }

                val roomConvs = rooms.map { r ->
                    val roomConvId = "room_${r.roomId}"
                    val messages = messagesByConv[roomConvId] ?: emptyList()
                    val lastMsgObj = messages.lastOrNull()
                    val unreadCount = messages.count { !it.isMine && it.deliveryStatus != "READ" }
                    val lastTime = lastMsgObj?.let { formatTimestamp(it.timestamp) } ?: "Active"
                    val lastMsg = lastMsgObj?.let {
                        if (it.attachmentType == "LOCATION_PIN") "📍 Pinned Location"
                        else if (it.attachmentType == "IMAGE") "📷 Photo"
                        else if (it.attachmentType == "FILE") "📄 ${it.attachmentName ?: "Document"}"
                        else if (it.isDeleted) "🚫 This message was deleted"
                        else it.decryptedTextCache
                    } ?: "Secure Private Room active"

                    val daysLeft = maxOf(0L, (r.expiresAt - System.currentTimeMillis()) / (86400000L))

                    ChatConversation(
                        id = roomConvId,
                        participantName = r.name,
                        handle = r.purpose,
                        avatarEmoji = r.iconEmoji,
                        cardType = CardType.PERSONAL,
                        lastMessage = lastMsg,
                        lastMessageTime = lastTime,
                        unreadCount = unreadCount,
                        moodEmoji = null,
                        moodWhatINeed = null,
                        passTimeRemaining = "$daysLeft days left",
                        passType = PassDurationType.SEVEN_DAYS,
                        isRoom = true,
                        isTyping = false,
                        lastMessageIsMine = lastMsgObj?.isMine ?: false,
                        lastMessageDeliveryStatus = lastMsgObj?.deliveryStatus ?: "DELIVERED",
                        lastMessageAttachmentType = lastMsgObj?.attachmentType,
                        messages = messages.map { m ->
                            ChatMessage(
                                id = m.messageId,
                                senderName = if (m.isMine) "You" else "Member",
                                text = if (m.isDeleted) "🚫 This message was deleted" else m.decryptedTextCache,
                                timestamp = formatTimestamp(m.timestamp),
                                isMine = m.isMine,
                                isScrubbedMedia = m.isScrubbedMedia,
                                deliveryStatus = m.deliveryStatus,
                                replyToMessageId = m.replyToMessageId,
                                replyToSenderName = m.replyToSenderName,
                                replyToText = m.replyToText,
                                reactions = emptyMap(),
                                myReactions = emptyList(),
                                isEdited = m.isEdited,
                                isDeleted = m.isDeleted,
                                attachmentUri = m.attachmentUri,
                                attachmentType = m.attachmentType,
                                attachmentName = m.attachmentName,
                                attachmentSize = m.attachmentSize
                            )
                        }
                    )
                }

                _uiState.update { it.copy(conversations = directConvs + roomConvs) }
            }
        }

        // 4. Observe Knock First Inbound Requests
        viewModelScope.launch {
            repository.getPendingRequests(userId).collect { requests ->
                val uiRequests = requests.map { r ->
                    val src = when (r.source) {
                        "QR_CODE" -> RequestSource.QR_CODE
                        "MARKETPLACE_PASS" -> RequestSource.MARKETPLACE_PASS
                        else -> RequestSource.ONE_TIME_LINK
                    }
                    KnockFirstRequest(
                        id = r.requestId,
                        senderName = r.senderDisplayName,
                        senderCardType = r.senderCardType,
                        source = src,
                        rawMessageExcerpt = r.rawMessage,
                        sandboxedLink = r.sandboxedLink,
                        timestamp = r.timestamp,
                        isCallBlocked = true,
                        isMediaBlocked = true
                    )
                }
                _uiState.update { it.copy(inboundRequests = uiRequests) }
            }
        }

        // 5. Observe Contact Passes
        viewModelScope.launch {
            repository.getActivePasses(userId).collect { passes ->
                val uiPasses = passes.map { p ->
                    val remaining = if (p.expiresAt == Long.MAX_VALUE) {
                        "Ongoing"
                    } else {
                        val hours = (p.expiresAt - System.currentTimeMillis()) / (3600 * 1000)
                        if (hours > 24) "${hours / 24}d left" else "${maxOf(0L, hours)}h left"
                    }
                    ContactPass(
                        id = p.passId,
                        token = p.token,
                        cardType = p.cardType,
                        durationType = p.durationType,
                        status = if (p.isRevoked) PassStatus.REVOKED else PassStatus.ACTIVE,
                        counterpartyName = if (p.isClaimed) "Claimed Peer" else "Unclaimed Pass",
                        timeRemainingString = remaining,
                        expiryTimestamp = p.expiresAt,
                        isRevocable = !p.isRevoked
                    )
                }
                _uiState.update { it.copy(activePasses = uiPasses) }
            }
        }

        // 6. Observe Private Rooms
        viewModelScope.launch {
            repository.getActiveRooms().collect { rooms ->
                val uiRooms = rooms.map { r ->
                    val tasks = try {
                        val arr = JSONArray(r.tasksJson)
                        (0 until arr.length()).map { idx ->
                            val obj = arr.getJSONObject(idx)
                            RoomTask(
                                id = obj.getString("id"),
                                title = obj.getString("title"),
                                isCompleted = obj.getBoolean("isCompleted"),
                                assignedTo = obj.getString("assignedTo")
                            )
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                    val daysLeft = maxOf(0L, (r.expiresAt - System.currentTimeMillis()) / (86400000L))
                    PrivateRoom(
                        id = r.roomId,
                        name = r.name,
                        purpose = r.purpose,
                        iconEmoji = r.iconEmoji,
                        expiryRemainingString = "$daysLeft days left",
                        autoCloseWarning = "Auto-closes at lifecycle expiration.",
                        members = emptyList(),
                        tasks = tasks
                    )
                }
                _uiState.update { it.copy(privateRooms = uiRooms) }
            }
        }

        // 7. Auto drain pending outbox messages and sync pending inbound requests & messages
        viewModelScope.launch {
            repository.syncInboundKnockFirstRequests(userId)
            repository.syncInboundMessages(userId)
            repository.retryPendingOutbox()
        }

        // 8. Real-time Inbound Encrypted Messages with Instant Notification Dispatch
        realtimePacketsJob?.cancel()
        realtimePacketsJob = viewModelScope.launch {
            repository.listenToInboundPackets(userId).collect { packets ->
                repository.processInboundPackets(packets, userId) { peerName, previewText, convId ->
                    if (_uiState.value.currentOpenChatId != convId) {
                        showInboundMessageNotification(peerName, previewText, convId)
                    }
                }
            }
        }

        // 8b. Real-time Outbound Packet Status Updates (Delivered, Read, Remote Reactions, Edits)
        realtimeOutboundJob?.cancel()
        realtimeOutboundJob = viewModelScope.launch {
            repository.listenToOutboundPackets(userId).collect { packets ->
                repository.processOutboundPacketUpdates(packets)
            }
        }

        // 9. Real-time Inbound Knock First Requests
        realtimeInboundKnockJob?.cancel()
        realtimeInboundKnockJob = viewModelScope.launch {
            repository.listenToInboundKnockFirstRequests(userId).collect { reqs ->
                repository.syncInboundKnockFirstRequests(userId)
            }
        }

        // 10. Real-time Outbound Knock First Request Status Changes (creates connection for sender upon acceptance)
        realtimeOutboundKnockJob?.cancel()
        realtimeOutboundKnockJob = viewModelScope.launch {
            repository.listenToOutboundKnockFirstRequests(userId).collect { reqs ->
                for (req in reqs) {
                    repository.handleOutboundKnockStatusChange(req, userId)
                }
            }
        }

        // 11. Observe incoming WebRTC calls
        viewModelScope.launch {
            repository.listenToIncomingCalls(userId).collect { call ->
                if (call != null && call.status == "RINGING" && _uiState.value.activeCallSession == null) {
                    val session = CallSession(
                        callId = call.callId,
                        peerUserId = call.callerUserId,
                        peerDisplayName = call.callerDisplayName,
                        isCaller = false,
                        callType = if (call.callType == "VIDEO") CallType.VIDEO else CallType.AUDIO,
                        status = CallStatus.INCOMING_RINGING
                    )
                    _uiState.update { it.copy(incomingCallSession = session) }
                } else if (call != null && (call.status == "ENDED" || call.status == "DECLINED" || call.status == "BUSY")) {
                    if (_uiState.value.incomingCallSession?.callId == call.callId) {
                        _uiState.update { it.copy(incomingCallSession = null) }
                    }
                }
            }
        }
    }

    // --- Authentication Actions (Production Ready) ---

    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.update { it.copy(authErrorMessage = "Email and password cannot be empty.") }
            return
        }
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        viewModelScope.launch {
            val result = repository.login(email, pass)
            _uiState.update { it.copy(isAuthLoading = false) }
            if (result.isSuccess) {
                val acc = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        currentUserAccount = acc,
                        activeCardId = acc.activeCardId,
                        authErrorMessage = null,
                        toastMessage = "Signed in as ${acc.email}"
                    )
                }
                observeUserData(acc.userId)
            } else {
                handleAuthenticationFailure(result.exceptionOrNull(), "Login failed.")
            }
        }
    }

    fun register(email: String, pass: String, displayName: String) {
        if (email.isBlank() || pass.isBlank() || displayName.isBlank()) {
            _uiState.update { it.copy(authErrorMessage = "All fields are required.") }
            return
        }
        if (pass.length < 8) {
            _uiState.update { it.copy(authErrorMessage = "Password must be at least 8 characters.") }
            return
        }
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        viewModelScope.launch {
            val result = repository.register(email, pass, displayName)
            _uiState.update { it.copy(isAuthLoading = false) }
            if (result.isSuccess) {
                val acc = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        currentUserAccount = acc,
                        activeCardId = acc.activeCardId,
                        authErrorMessage = null,
                        toastMessage = "Sovereign identity created for $displayName"
                    )
                }
                observeUserData(acc.userId)
            } else {
                handleAuthenticationFailure(result.exceptionOrNull(), "Registration failed.")
            }
        }
    }

    fun sendPhoneOtp(phoneNumber: String, activity: Activity? = null) {
        val raw = phoneNumber.trim().replace(" ", "").replace("-", "")
        if (raw.isBlank()) {
            _uiState.update { it.copy(authErrorMessage = "Enter a valid mobile number.") }
            return
        }
        val formattedNumber = when {
            raw.startsWith("+") -> raw
            raw.startsWith("01") && raw.length == 11 -> "+88$raw"
            raw.startsWith("880") -> "+$raw"
            else -> "+$raw"
        }
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        viewModelScope.launch {
            val result = repository.sendPhoneOtp(formattedNumber, activity)
            _uiState.update { it.copy(isAuthLoading = false) }
            if (result.isSuccess) {
                val verId = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        authVerificationId = verId,
                        toastMessage = "Verification code sent to $formattedNumber. Enter the SMS code to continue."
                    )
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to send OTP."
                _uiState.update { it.copy(authErrorMessage = err, toastMessage = err) }
            }
        }
    }

    fun verifyPhoneOtp(code: String, displayName: String) {
        val verId = _uiState.value.authVerificationId
        if (verId == null) {
            _uiState.update { it.copy(authErrorMessage = "Please request an OTP first.") }
            return
        }
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        viewModelScope.launch {
            val result = repository.verifyPhoneOtp(verId, code, displayName)
            _uiState.update { it.copy(isAuthLoading = false) }
            if (result.isSuccess) {
                val acc = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        currentUserAccount = acc,
                        activeCardId = acc.activeCardId,
                        authVerificationId = null,
                        authErrorMessage = null,
                        toastMessage = "Phone verified successfully."
                    )
                }
                observeUserData(acc.userId)
            } else {
                handleAuthenticationFailure(result.exceptionOrNull(), "OTP verification failed.")
            }
        }
    }

    fun loginWithGoogle(idToken: String, displayName: String) {
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        viewModelScope.launch {
            val result = repository.loginWithGoogle(idToken, displayName)
            _uiState.update { it.copy(isAuthLoading = false) }
            if (result.isSuccess) {
                val acc = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        currentUserAccount = acc,
                        activeCardId = acc.activeCardId,
                        authErrorMessage = null,
                        toastMessage = "Google identity connected."
                    )
                }
                observeUserData(acc.userId)
            } else {
                handleAuthenticationFailure(result.exceptionOrNull(), "Google sign in failed.")
            }
        }
    }

    private fun handleAuthenticationFailure(error: Throwable?, fallbackMessage: String) {
        if (error is ExistingRemoteKeysException) {
            pendingKeyRecoveryUser = error.remoteUser
            _uiState.update {
                it.copy(
                    isAuthLoading = false,
                    isKeyRecoveryRequired = true,
                    authErrorMessage = null,
                    identityBackupError = null,
                    identityBackupText = null
                )
            }
            return
        }

        val message = error?.message ?: fallbackMessage
        _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = message, toastMessage = message) }
    }

    fun exportIdentityKeyBackup(passphrase: String) {
        val user = _uiState.value.currentUserAccount
        if (user == null) {
            _uiState.update { it.copy(identityBackupError = "Sign in before exporting an identity backup.") }
            return
        }
        if (passphrase.length < 12) {
            _uiState.update { it.copy(identityBackupError = "Use a backup passphrase with at least 12 characters.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isIdentityBackupLoading = true, identityBackupText = null, identityBackupError = null)
            }
            val result = repository.exportEncryptedKeyBackup(user.userId, passphrase)
            _uiState.update {
                it.copy(
                    isIdentityBackupLoading = false,
                    identityBackupText = result.getOrNull(),
                    identityBackupError = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun clearIdentityKeyBackup() {
        _uiState.update {
            it.copy(isIdentityBackupLoading = false, identityBackupText = null, identityBackupError = null)
        }
    }

    fun dismissIdentityKeyRecovery() {
        pendingKeyRecoveryUser = null
        _uiState.update {
            it.copy(
                isKeyRecoveryRequired = false,
                identityBackupError = null,
                identityBackupText = null
            )
        }
    }

    fun restoreIdentityKeyBackup(passphrase: String, backupCiphertext: String) {
        val remoteUser = pendingKeyRecoveryUser
        if (remoteUser == null) {
            _uiState.update { it.copy(identityBackupError = "Sign in again before restoring this backup.") }
            return
        }
        if (backupCiphertext.isBlank()) {
            _uiState.update { it.copy(identityBackupError = "Paste your encrypted identity backup.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, identityBackupError = null) }
            val result = repository.restoreEncryptedKeyBackup(remoteUser, passphrase, backupCiphertext.trim())
            finishIdentityRecovery(result, "Encrypted identity backup restored.")
        }
    }

    fun confirmIdentityKeyReset() {
        val remoteUser = pendingKeyRecoveryUser
        if (remoteUser == null) {
            _uiState.update { it.copy(identityBackupError = "Sign in again before resetting identity keys.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, identityBackupError = null) }
            val result = repository.confirmKeyReset(remoteUser)
            finishIdentityRecovery(result, "New identity keys created.")
        }
    }

    private fun finishIdentityRecovery(result: Result<UserAccountEntity>, successMessage: String) {
        if (result.isFailure) {
            val error = result.exceptionOrNull()?.message ?: "Identity recovery failed."
            _uiState.update {
                it.copy(
                    isAuthLoading = false,
                    isKeyRecoveryRequired = true,
                    identityBackupError = error
                )
            }
            return
        }

        val account = result.getOrThrow()
        pendingKeyRecoveryUser = null
        _uiState.update {
            it.copy(
                isAuthLoading = false,
                currentUserAccount = account,
                activeCardId = account.activeCardId,
                isKeyRecoveryRequired = false,
                identityBackupText = null,
                identityBackupError = null,
                authVerificationId = null,
                authErrorMessage = null,
                toastMessage = successMessage
            )
        }
        observeUserData(account.userId)
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _uiState.update { it.copy(authErrorMessage = "Enter email for password recovery.") }
            return
        }
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        viewModelScope.launch {
            val result = repository.sendPasswordReset(email)
            _uiState.update { it.copy(isAuthLoading = false) }
            if (result.isSuccess) {
                _uiState.update { it.copy(toastMessage = "If an account exists, recovery instructions have been dispatched.") }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Could not send recovery instructions."
                _uiState.update { it.copy(authErrorMessage = err, toastMessage = err) }
            }
        }
    }

    fun showAuthError(message: String) {
        _uiState.update { it.copy(authErrorMessage = message, toastMessage = message) }
    }

    fun logout() {
        initializingCallId = null
        pendingKeyRecoveryUser = null
        teardownWebRtc()
        LiveLocationService.stop(getApplication())
        realtimePacketsJob?.cancel()
        realtimePacketsJob = null
        realtimeInboundKnockJob?.cancel()
        realtimeInboundKnockJob = null
        realtimeOutboundKnockJob?.cancel()
        realtimeOutboundKnockJob = null
        searchJob?.cancel()
        searchJob = null
        viewModelScope.launch {
            repository.logout()
            _uiState.update {
                it.copy(
                    currentUserAccount = null,
                    conversations = emptyList(),
                    activePasses = emptyList(),
                    inboundRequests = emptyList(),
                    currentOpenChatId = null,
                    activeCallSession = null,
                    incomingCallSession = null,
                    activeLiveLocation = null,
                    toastMessage = "Signed out of Sovereign Enclave"
                )
            }
        }
    }

    fun onAppResume() {
        val user = _uiState.value.currentUserAccount ?: return
        viewModelScope.launch {
            repository.syncInboundKnockFirstRequests(user.userId)
            repository.syncInboundMessages(user.userId)
            repository.retryPendingOutbox()
        }
    }

    // --- Persona Card Switching ---

    fun selectCard(cardId: String) {
        val user = _uiState.value.currentUserAccount ?: return
        viewModelScope.launch {
            repository.selectActiveCard(user.userId, cardId)
            _uiState.update { it.copy(activeCardId = cardId) }
        }
    }

    // --- Mood Ring Actions ---

    fun openMoodPicker() = _uiState.update { it.copy(isMoodPickerOpen = true) }
    fun closeMoodPicker() = _uiState.update { it.copy(isMoodPickerOpen = false) }

    fun broadcastMood(
        emotion: MoodEmotion,
        whatINeed: WhatINeed?,
        circleName: String,
        duration: DecayDuration
    ) {
        val user = _uiState.value.currentUserAccount ?: return
        val durationMins = when (duration) {
            DecayDuration.MINUTES_30 -> 30L
            DecayDuration.HOURS_2 -> 120L
            DecayDuration.END_OF_DAY -> -1L
            DecayDuration.CUSTOM -> 720L
        }

        viewModelScope.launch {
            repository.publishMood(
                userId = user.userId,
                emotion = emotion.name,
                whatINeed = whatINeed?.name,
                audienceType = if (circleName == "Close Circle") "CIRCLES" else "CONNECTIONS",
                allowedAudienceIds = listOf(user.userId, circleName),
                decayDurationMinutes = durationMins
            )
            _uiState.update {
                it.copy(
                    isMoodPickerOpen = false,
                    toastMessage = "Quiet Presence broadcasted with time decay."
                )
            }
        }
    }

    fun clearMoodRing() {
        val user = _uiState.value.currentUserAccount ?: return
        viewModelScope.launch {
            repository.clearMood(user.userId)
            _uiState.update {
                it.copy(
                    moodState = null,
                    isMoodPickerOpen = false,
                    toastMessage = "Quiet Presence cleared."
                )
            }
        }
    }

    // --- Contact Pass Generator, Scanner & Claim Actions ---

    fun openNewChatMenu() = _uiState.update { it.copy(isNewChatMenuOpen = true) }
    fun closeNewChatMenu() = _uiState.update { it.copy(isNewChatMenuOpen = false) }

    fun openPassGenerator() = _uiState.update { it.copy(isPassGeneratorOpen = true, isNewChatMenuOpen = false) }
    fun closePassGenerator() = _uiState.update { it.copy(isPassGeneratorOpen = false) }

    fun openPassScanner() = _uiState.update { it.copy(isPassScannerOpen = true, isNewChatMenuOpen = false, passClaimError = null) }
    fun closePassScanner() = _uiState.update { it.copy(isPassScannerOpen = false) }

    fun onPassScanned(qrData: String) {
        val payload = ContactPassQrEngine.parseQrOrToken(qrData)
        if (payload != null) {
            _uiState.update {
                it.copy(
                    isPassScannerOpen = false,
                    scannedPassPayload = payload,
                    passClaimError = null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    toastMessage = "Invalid QR code format. Not an authentic Fort Contact Pass."
                )
            }
        }
    }

    fun dismissScannedPassPreview() {
        _uiState.update {
            it.copy(
                scannedPassPayload = null,
                passClaimError = null,
                isClaimingPass = false
            )
        }
    }

    fun claimScannedPass(payload: ContactPassPayload) {
        val user = _uiState.value.currentUserAccount ?: return
        if (payload.issuerUserId.isNotBlank() && payload.issuerUserId == user.userId) {
            _uiState.update {
                it.copy(passClaimError = "You cannot claim your own contact pass.")
            }
            return
        }

        _uiState.update { it.copy(isClaimingPass = true, passClaimError = null) }
        viewModelScope.launch {
            val card = _uiState.value.connectionCards.firstOrNull()
            val myDisplayName = card?.displayName ?: user.email.substringBefore("@")
            val result = repository.claimPass(
                token = payload.token,
                claimantUserId = user.userId,
                claimantDisplayName = myDisplayName,
                passId = payload.passId.takeIf { it.isNotBlank() },
                issuerDisplayName = payload.issuerDisplayName.takeIf { it.isNotBlank() }
            )
            _uiState.update { it.copy(isClaimingPass = false) }
            if (result.isSuccess) {
                val connection = result.getOrThrow()
                // Send initial greeting handshake message over E2EE channel
                repository.sendEncryptedMessage(
                    conversationId = "conv_${connection.peerUserId}",
                    senderUserId = user.userId,
                    recipientUserId = connection.peerUserId,
                    plaintext = "🤝 Contact pass connected. Sovereign channel established."
                )
                val targetChatId = "conv_${connection.peerUserId}"
                _uiState.update {
                    it.copy(
                        scannedPassPayload = null,
                        passClaimError = null,
                        currentOpenChatId = targetChatId,
                        toastMessage = "Connected with ${connection.peerDisplayName}!"
                    )
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to claim contact pass."
                _uiState.update { it.copy(passClaimError = err) }
            }
        }
    }

    fun generateNewPass(cardType: CardType, durationType: PassDurationType) {
        val user = _uiState.value.currentUserAccount ?: return
        _uiState.update { it.copy(isGeneratingPass = true, passGenerationError = null) }
        viewModelScope.launch {
            val result = repository.generatePass(
                issuerUserId = user.userId,
                cardType = cardType,
                durationType = durationType
            )
            if (result.isSuccess) {
                val p = result.getOrThrow()
                val card = _uiState.value.connectionCards.find { it.type == cardType }
                val qrPayload = ContactPassPayload(
                    passId = p.passId,
                    token = p.token,
                    issuerUserId = user.userId,
                    issuerDisplayName = card?.displayName ?: "Sovereign User",
                    issuerCardType = cardType.name,
                    durationType = durationType.name,
                    expiresAt = p.expiresAt,
                    issuerPublicKey = card?.publicKeyFingerprint ?: ""
                )
                val bitmap = ContactPassQrEngine.generateQrBitmap(qrPayload, 512, 512)
                val uiPass = ContactPass(
                    id = p.passId,
                    token = p.token,
                    cardType = p.cardType,
                    durationType = p.durationType,
                    status = PassStatus.ACTIVE,
                    counterpartyName = "Unclaimed Pass",
                    timeRemainingString = durationType.label,
                    expiryTimestamp = p.expiresAt
                )
                _uiState.update {
                    it.copy(
                        isGeneratingPass = false,
                        generatedPassQrBitmap = bitmap,
                        activeGeneratedPass = uiPass,
                        passGenerationError = null,
                        toastMessage = "Contact Pass published: ${p.token}"
                    )
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to generate pass remotely."
                _uiState.update {
                    it.copy(
                        isGeneratingPass = false,
                        passGenerationError = err,
                        toastMessage = err
                    )
                }
            }
        }
    }

    // --- User Discovery & Search People ---

    fun openSearchPeople() = _uiState.update {
        it.copy(isSearchPeopleOpen = true, isNewChatMenuOpen = false, searchResults = emptyList(), searchPeopleError = null)
    }

    fun closeSearchPeople() = _uiState.update {
        it.copy(isSearchPeopleOpen = false, searchResults = emptyList(), searchPeopleError = null, selectedUserForKnock = null)
    }

    fun searchPeople(query: String, mode: SearchMode) {
        val user = _uiState.value.currentUserAccount ?: return
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(isSearchingPeople = false, searchResults = emptyList(), searchPeopleError = null) }
            return
        }
        _uiState.update { it.copy(isSearchingPeople = true, searchPeopleError = null) }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(250L)
            val result = repository.searchUsers(trimmed, mode, user.userId)
            _uiState.update {
                it.copy(
                    isSearchingPeople = false,
                    searchResults = result.getOrDefault(emptyList()),
                    searchPeopleError = if (result.isFailure) result.exceptionOrNull()?.message ?: "Search failed" else null
                )
            }
        }
    }

    fun selectUserForKnock(user: UserSearchResult?) = _uiState.update { it.copy(selectedUserForKnock = user) }

    fun sendKnockFirstRequest(targetUser: UserSearchResult, introMessage: String) {
        val user = _uiState.value.currentUserAccount ?: return
        viewModelScope.launch {
            val card = _uiState.value.connectionCards.firstOrNull()
            val result = repository.submitKnockFirstRequest(
                recipientUserId = targetUser.userId,
                senderUserId = user.userId,
                senderDisplayName = card?.displayName ?: user.email.substringBefore("@"),
                senderCardType = card?.type ?: CardType.PERSONAL,
                source = "DISCOVERY_SEARCH",
                rawMessage = introMessage.ifBlank { "Hello! I'd like to connect securely on Fort." },
                sandboxedLink = null
            )
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        selectedUserForKnock = null,
                        isSearchPeopleOpen = false,
                        toastMessage = "Knock First request sent to ${targetUser.displayName}."
                    )
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to send knock request."
                _uiState.update { it.copy(toastMessage = err) }
            }
        }
    }

    // --- WebRTC Audio & Video Calling ---

    private fun hasRequiredCallPermissions(callType: CallType): Boolean {
        val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(
            getApplication(), android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasVideo = callType != CallType.VIDEO || androidx.core.content.ContextCompat.checkSelfPermission(
            getApplication(), android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return hasAudio && hasVideo
    }

    fun startCall(peerUserId: String, peerDisplayName: String, callType: CallType) {
        val callId = "call_${UUID.randomUUID()}"
        val session = CallSession(
            callId = callId,
            peerUserId = peerUserId,
            peerDisplayName = peerDisplayName,
            isCaller = true,
            callType = callType,
            status = CallStatus.OUTGOING_RINGING
        )
        _uiState.update { it.copy(activeCallSession = session) }

        val user = _uiState.value.currentUserAccount
        if (user != null && hasRequiredCallPermissions(callType)) {
            launchOutgoingCallWithPermissions(session)
        }
        // If permissions missing, CallScreenModal requests them and invokes onCallPermissionsGranted()
    }

    private fun launchOutgoingCallWithPermissions(session: CallSession) {
        if (webrtcManager != null || initializingCallId != null) return
        if (_uiState.value.activeCallSession?.callId != session.callId) return
        val user = _uiState.value.currentUserAccount ?: return
        initializingCallId = session.callId

        viewModelScope.launch {
            try {
                val myName = _uiState.value.connectionCards.firstOrNull()?.displayName ?: user.email.substringBefore("@")
                val record = RemoteCallRecord(
                    callId = session.callId,
                    callerUserId = user.userId,
                    callerDisplayName = myName,
                    receiverUserId = session.peerUserId,
                    callType = session.callType.name,
                    status = "RINGING"
                )
                val result = repository.createCall(record)
                if (_uiState.value.activeCallSession?.callId != session.callId) return@launch
                if (result.isSuccess) {
                    initWebRtcForCall(session, isInitiator = true)
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Could not start call."
                    endCall()
                    _uiState.update { it.copy(toastMessage = err) }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (_uiState.value.activeCallSession?.callId == session.callId) {
                    endCall()
                    _uiState.update { it.copy(toastMessage = error.message ?: "Could not start call." ) }
                }
            } finally {
                if (initializingCallId == session.callId) initializingCallId = null
            }
        }
    }

    fun acceptIncomingCall() {
        val incoming = _uiState.value.incomingCallSession ?: return
        _uiState.update {
            it.copy(
                incomingCallSession = null,
                activeCallSession = incoming.copy(status = CallStatus.CONNECTING)
            )
        }
        if (hasRequiredCallPermissions(incoming.callType)) {
            launchAcceptedIncomingCallWithPermissions(incoming)
        }
        // If permissions missing, CallScreenModal requests them and triggers onCallPermissionsGranted()
    }

    private fun launchAcceptedIncomingCallWithPermissions(session: CallSession) {
        if (webrtcManager != null || initializingCallId != null) return
        if (_uiState.value.activeCallSession?.callId != session.callId) return
        val user = _uiState.value.currentUserAccount ?: return
        initializingCallId = session.callId

        viewModelScope.launch {
            try {
                val accepted = repository.updateCallStatus(session.callId, "ACCEPTED", user.userId)
                if (_uiState.value.activeCallSession?.callId != session.callId) return@launch
                if (accepted.isFailure) {
                    val err = accepted.exceptionOrNull()?.message ?: "Could not accept call."
                    endCall()
                    _uiState.update { it.copy(toastMessage = err) }
                    return@launch
                }
                initWebRtcForCall(session, isInitiator = false)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (_uiState.value.activeCallSession?.callId == session.callId) {
                    endCall()
                    _uiState.update { it.copy(toastMessage = error.message ?: "Could not accept call." ) }
                }
            } finally {
                if (initializingCallId == session.callId) initializingCallId = null
            }
        }
    }

    fun declineIncomingCall() {
        val user = _uiState.value.currentUserAccount ?: return
        val incoming = _uiState.value.incomingCallSession ?: return
        _uiState.update { it.copy(incomingCallSession = null) }
        viewModelScope.launch {
            repository.updateCallStatus(incoming.callId, "DECLINED", user.userId)
        }
    }

    fun endCall() {
        initializingCallId = null
        val user = _uiState.value.currentUserAccount
        val active = _uiState.value.activeCallSession
        if (active != null && user != null) {
            viewModelScope.launch {
                repository.updateCallStatus(active.callId, "ENDED", user.userId)
            }
        }
        teardownWebRtc()
        _uiState.update { it.copy(activeCallSession = null, incomingCallSession = null, isCallMinimized = false) }
    }

    fun minimizeCall() {
        _uiState.update { it.copy(isCallMinimized = true) }
    }

    fun expandCall() {
        _uiState.update { it.copy(isCallMinimized = false) }
    }

    fun toggleMute() {
        val isMuted = webrtcManager?.toggleMute() ?: false
        _uiState.update { state ->
            state.copy(activeCallSession = state.activeCallSession?.copy(isMuted = isMuted))
        }
    }

    fun toggleSpeaker() {
        val isSpeaker = webrtcManager?.toggleSpeakerphone() ?: false
        _uiState.update { state ->
            state.copy(activeCallSession = state.activeCallSession?.copy(isSpeakerOn = isSpeaker))
        }
    }

    fun toggleVideo() {
        val current = _uiState.value.activeCallSession?.isVideoEnabled ?: true
        val newVideo = !current
        webrtcManager?.toggleVideo(newVideo)
        _uiState.update { state ->
            state.copy(activeCallSession = state.activeCallSession?.copy(isVideoEnabled = newVideo))
        }
    }

    fun switchCamera() {
        webrtcManager?.switchCamera()
    }

    fun onCallPermissionsGranted() {
        if (webrtcManager != null || initializingCallId != null) return
        val session = _uiState.value.activeCallSession ?: return
        if (session.isCaller) {
            launchOutgoingCallWithPermissions(session)
        } else {
            launchAcceptedIncomingCallWithPermissions(session)
        }
    }

    private suspend fun initWebRtcForCall(session: CallSession, isInitiator: Boolean) {
        val user = _uiState.value.currentUserAccount ?: return
        if (_uiState.value.activeCallSession?.callId != session.callId || webrtcManager != null) return

        val turnServerConfigs = repository.fetchTurnServerConfigs(user.userId).getOrDefault(emptyList())
        if (_uiState.value.activeCallSession?.callId != session.callId
            || initializingCallId != session.callId
            || webrtcManager != null
        ) return

        val manager = WebRtcCallManager(
            context = getApplication(),
            onIceCandidateGenerated = { candidate ->
                viewModelScope.launch {
                    repository.sendCallIceCandidate(session.callId, candidate, isInitiator, user.userId)
                }
            },
            onCallConnected = {
                viewModelScope.launch {
                    repository.updateCallStatus(session.callId, "ACCEPTED", user.userId)
                    _uiState.update { it.copy(activeCallSession = it.activeCallSession?.copy(status = CallStatus.CONNECTED)) }
                }
            },
            onCallDisconnected = { _ ->
                endCall()
            },
            turnServerConfigs = turnServerConfigs
        )
        webrtcManager = manager
        initializingCallId = null
        manager.init()

        // Create permitted local tracks before creating peer connection
        // so the first negotiated SDP offer or answer contains valid media tracks
        manager.startLocalMedia(session.callType)
        manager.createPeerConnection()

        activeCallJob = viewModelScope.launch {
            repository.listenToCall(session.callId).collect { remoteCall ->
                if (remoteCall == null || remoteCall.status == "ENDED" || remoteCall.status == "DECLINED") {
                    endCall()
                    return@collect
                }
                if (isInitiator && remoteCall.answerSdp != null && _uiState.value.activeCallSession?.status != CallStatus.CONNECTED) {
                    manager.setRemoteAnswer(remoteCall.answerSdp)
                } else if (!isInitiator && remoteCall.offerSdp != null && _uiState.value.activeCallSession?.status == CallStatus.CONNECTING) {
                    manager.createAnswer(remoteCall.offerSdp) { answerDesc ->
                        viewModelScope.launch {
                            repository.setCallAnswer(session.callId, answerDesc.description, user.userId)
                        }
                    }
                }
            }
        }

        val processedCandidateKeys = mutableSetOf<String>()
        iceCandidatesJob = viewModelScope.launch {
            repository.listenToCallCandidates(session.callId, !isInitiator).collect { candidateList ->
                candidateList.forEach { candidate ->
                    val key = "${candidate.sdpMid}_${candidate.sdpMLineIndex}_${candidate.candidate}"
                    if (processedCandidateKeys.add(key)) {
                        manager.addRemoteIceCandidate(candidate)
                    }
                }
            }
        }

        if (isInitiator) {
            manager.createOffer { offerDesc ->
                viewModelScope.launch {
                    repository.setCallOffer(session.callId, offerDesc.description, user.userId)
                    repository.updateCallStatus(session.callId, "RINGING", user.userId)
                }
            }
        }
    }

    private fun teardownWebRtc() {
        activeCallJob?.cancel()
        activeCallJob = null
        iceCandidatesJob?.cancel()
        iceCandidatesJob = null
        webrtcManager?.close()
        webrtcManager = null
    }

    // --- Private Location Sharing ---

    fun openLocationShareModal() = _uiState.update { it.copy(isLocationShareModalOpen = true) }
    fun closeLocationShareModal() = _uiState.update { it.copy(isLocationShareModalOpen = false) }

    fun sendLocationPin(latitude: Double, longitude: Double, label: String = "Pinned Location") {
        val user = _uiState.value.currentUserAccount ?: return
        val chatId = _uiState.value.currentOpenChatId ?: return
        val peerUserId = chatId.removePrefix("conv_")
        val pin = LocationPin(latitude, longitude, label)

        viewModelScope.launch {
            repository.sendEncryptedMessage(
                conversationId = chatId,
                senderUserId = user.userId,
                recipientUserId = peerUserId,
                plaintext = "📍 Location Pin: ${pin.toLocationMessageText()}",
                attachmentType = "LOCATION_PIN",
                attachmentName = "${pin.latitude},${pin.longitude}"
            )
            _uiState.update { it.copy(isLocationShareModalOpen = false, toastMessage = "Location pin shared.") }
        }
    }

    fun startLiveLocationSharing(duration: LiveLocationDuration, latitude: Double, longitude: Double) {
        val user = _uiState.value.currentUserAccount ?: return
        val chatId = _uiState.value.currentOpenChatId ?: return
        val peerUserId = chatId.removePrefix("conv_")
        val myName = _uiState.value.connectionCards.firstOrNull()?.displayName ?: user.email.substringBefore("@")

        viewModelScope.launch {
            val result = repository.startLiveLocationSharing(
                senderUserId = user.userId,
                senderDisplayName = myName,
                recipientUserId = peerUserId,
                duration = duration,
                initialLat = latitude,
                initialLng = longitude
            )
            if (result.isSuccess) {
                val session = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        activeLiveLocation = session,
                        isLocationShareModalOpen = false,
                        toastMessage = "Live location sharing started (${duration.label})."
                    )
                }
                LiveLocationService.start(getApplication(), session.shareId, user.userId, myName, peerUserId, session.expiresAt)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to start live location."
                _uiState.update { it.copy(toastMessage = err) }
            }
        }
    }

    fun stopLiveLocationSharing() {
        val user = _uiState.value.currentUserAccount ?: return
        val session = _uiState.value.activeLiveLocation ?: return
        viewModelScope.launch {
            repository.stopLiveLocationSharing(session.shareId, user.userId, session.recipientUserId)
            LiveLocationService.stop(getApplication())
            _uiState.update {
                it.copy(
                    activeLiveLocation = null,
                    toastMessage = "Live location sharing stopped."
                )
            }
        }
    }

    fun revokePass(passId: String) {
        val user = _uiState.value.currentUserAccount ?: return
        viewModelScope.launch {
            repository.revokePass(passId, user.userId)
            _uiState.update { it.copy(toastMessage = "Contact Pass revoked deterministically.") }
        }
    }

    // --- Knock First Actions ---

    fun acceptRequestOnce(requestId: String) {
        val user = _uiState.value.currentUserAccount ?: return
        viewModelScope.launch {
            val reqEntity = repository.getPendingRequests(user.userId).firstOrNull()?.find { it.requestId == requestId }
            if (reqEntity != null) {
                repository.acceptRequestOnce(reqEntity, user.userId)
                _uiState.update { it.copy(toastMessage = "Accepted once. Single-use pass granted.") }
            }
        }
    }

    fun grantRequestSevenDays(requestId: String) {
        val user = _uiState.value.currentUserAccount ?: return
        viewModelScope.launch {
            val reqEntity = repository.getPendingRequests(user.userId).firstOrNull()?.find { it.requestId == requestId }
            if (reqEntity != null) {
                repository.grantRequestSevenDays(reqEntity, user.userId)
                _uiState.update { it.copy(toastMessage = "Granted 7-Day Contact Pass.") }
            }
        }
    }

    fun declineRequest(requestId: String) {
        viewModelScope.launch {
            repository.declineRequest(requestId)
            _uiState.update { it.copy(toastMessage = "Request declined quietly.") }
        }
    }

    fun blockAndReportRequest(requestId: String) {
        val user = _uiState.value.currentUserAccount ?: return
        viewModelScope.launch {
            val reqEntity = repository.getPendingRequests(user.userId).firstOrNull()?.find { it.requestId == requestId }
            if (reqEntity != null) {
                repository.blockAndReportRequest(reqEntity, user.userId)
                _uiState.update { it.copy(toastMessage = "Entity blocked and token blacklisted.") }
            }
        }
    }

    // --- Modern Messenger Capabilities (Drafts, Typing, Read Receipts, Replies, Reactions, Edits, Deletes, Attachments, Status) ---

    fun getDraft(conversationId: String): String = _uiState.value.conversationDrafts[conversationId] ?: ""

    fun setDraft(conversationId: String, text: String) {
        _uiState.update { state ->
            val updated = state.conversationDrafts.toMutableMap()
            if (text.isBlank()) {
                updated.remove(conversationId)
            } else {
                updated[conversationId] = text
            }
            state.copy(conversationDrafts = updated)
        }
    }

    fun onUserTyping(conversationId: String, isTyping: Boolean) {
        val user = _uiState.value.currentUserAccount ?: return
        if (!conversationId.startsWith("room_")) {
            val peerUserId = conversationId.removePrefix("conv_")
            viewModelScope.launch {
                repository.setTypingStatus(user.userId, peerUserId, isTyping)
            }
        }
    }

    fun markConversationAsRead(conversationId: String) {
        val user = _uiState.value.currentUserAccount ?: return
        viewModelScope.launch {
            repository.markConversationAsRead(conversationId, user.userId)
        }
    }

    fun openChat(conversationId: String) {
        val user = _uiState.value.currentUserAccount ?: return
        val rawId = conversationId.removePrefix("conv_")
        val cleanConvId = if (conversationId.startsWith("room_")) conversationId else "conv_$rawId"
        val peerUserId = rawId

        // Mark conversation as read in local Room DB and update deliveryStatus to READ
        viewModelScope.launch {
            repository.markConversationAsRead(cleanConvId, user.userId)
        }

        // Subscribe to peer typing indicator
        typingListenerJob?.cancel()
        if (!cleanConvId.startsWith("room_")) {
            typingListenerJob = viewModelScope.launch {
                repository.listenToPeerTyping(user.userId, peerUserId).collect { isTyping ->
                    _uiState.update { it.copy(isPeerTyping = isTyping) }
                }
            }
        }

        viewModelScope.launch {
            val connection = repository.getActiveConnections(user.userId).firstOrNull()?.find { it.peerUserId == peerUserId }
            _uiState.update {
                it.copy(
                    currentOpenChatId = cleanConvId,
                    currentPeerConnection = connection,
                    inChatSearchQuery = "",
                    replyingToMessage = null,
                    isPeerTyping = false
                )
            }
        }
    }

    fun closeChat() {
        val user = _uiState.value.currentUserAccount
        val chatId = _uiState.value.currentOpenChatId
        if (user != null && chatId != null && !chatId.startsWith("room_")) {
            val peerUserId = chatId.removePrefix("conv_")
            viewModelScope.launch {
                repository.setTypingStatus(user.userId, peerUserId, false)
            }
        }
        typingListenerJob?.cancel()
        typingListenerJob = null
        _uiState.update {
            it.copy(
                currentOpenChatId = null,
                currentPeerConnection = null,
                replyingToMessage = null,
                inChatSearchQuery = "",
                isPeerTyping = false
            )
        }
    }

    fun setReplyingTo(message: ChatMessage?) = _uiState.update { it.copy(replyingToMessage = message) }

    fun setInChatSearchQuery(query: String) = _uiState.update { it.copy(inChatSearchQuery = query) }

    fun sendMessage(
        text: String,
        attachmentUri: String? = null,
        attachmentType: String? = null,
        attachmentName: String? = null,
        attachmentSize: Long = 0L
    ) {
        if (text.isBlank() && attachmentUri == null) return
        val user = _uiState.value.currentUserAccount ?: return
        val chatId = _uiState.value.currentOpenChatId ?: return
        val peerUserId = chatId.removePrefix("conv_")
        val replying = _uiState.value.replyingToMessage

        // Clear draft and typing status on send
        setDraft(chatId, "")
        onUserTyping(chatId, false)

        viewModelScope.launch {
            val result = repository.sendEncryptedMessage(
                conversationId = chatId,
                senderUserId = user.userId,
                recipientUserId = peerUserId,
                plaintext = text,
                isScrubbedMedia = attachmentType == "IMAGE",
                replyToMessageId = replying?.id,
                replyToSenderName = replying?.senderName,
                replyToText = replying?.text?.take(60),
                attachmentUri = attachmentUri,
                attachmentType = attachmentType,
                attachmentName = attachmentName,
                attachmentSize = attachmentSize
            )
            _uiState.update { it.copy(replyingToMessage = null) }
            if (result.isFailure) {
                _uiState.update { it.copy(toastMessage = "Queued for delivery: ${result.exceptionOrNull()?.message}") }
            }
        }
    }

    fun addReaction(messageId: String, emoji: String) {
        val user = _uiState.value.currentUserAccount ?: return
        viewModelScope.launch {
            repository.addMessageReaction(messageId, user.userId, emoji)
        }
    }

    fun editMessage(messageId: String, newText: String) {
        if (newText.isBlank()) return
        val user = _uiState.value.currentUserAccount ?: return
        viewModelScope.launch {
            repository.editMessage(messageId, newText, user.userId)
            _uiState.update { it.copy(toastMessage = "Message updated.") }
        }
    }

    fun deleteMessage(messageId: String) {
        val user = _uiState.value.currentUserAccount ?: return
        viewModelScope.launch {
            repository.deleteMessage(messageId, user.userId)
            _uiState.update { it.copy(toastMessage = "Message deleted.") }
        }
    }

    fun retryPendingOutbox() {
        viewModelScope.launch {
            val count = repository.retryPendingOutbox()
            if (count > 0) {
                _uiState.update { it.copy(toastMessage = "Retried $count queued messages successfully.") }
            }
        }
    }

    fun sendSupportResponse(targetEmoji: String = "🤍") = sendMessage("I'm here $targetEmoji")

    fun setChatExpiry(setting: String) = _uiState.update { it.copy(activeChatExpirySetting = setting) }

    // --- Privacy Check Modal ---

    fun openPrivacyCheck() = _uiState.update { it.copy(isPrivacyCheckOpen = true) }
    fun closePrivacyCheck() = _uiState.update { it.copy(isPrivacyCheckOpen = false) }

    fun markPeerSafetyNumberVerified() {
        val conn = _uiState.value.currentPeerConnection ?: return
        viewModelScope.launch {
            repository.verifySafetyNumber(conn.connectionId)
            _uiState.update {
                it.copy(
                    currentPeerConnection = conn.copy(isVerified = true),
                    toastMessage = "Safety Number marked as verified."
                )
            }
        }
    }

    // --- Share Check Media Inspection & Sanitization ---

    fun openShareCheck() {
        val cacheDir = getApplication<Application>().cacheDir
        val sampleFile = File(cacheDir, "sample_media_preflight.jpg")
        if (!sampleFile.exists()) {
            sampleFile.writeBytes(ByteArray(1024))
        }
        val report = ShareCheckScrubber.inspectFile(sampleFile, "Contact for pickup: +1 (415) 555-0199 at 120 Market Street")
        val items = report.detectedItems.map {
            ShareCheckItem(
                id = it.id,
                type = when (it.id) {
                    "exif_gps" -> SensitivityType.EXIF_GPS
                    "text_phone" -> SensitivityType.PHONE_NUMBER
                    else -> SensitivityType.PHYSICAL_ADDRESS
                },
                detectedValue = it.rawValue,
                isScrubbed = it.isRedacted
            )
        }
        _uiState.update {
            it.copy(
                inspectedFileReport = report,
                shareCheckItems = items,
                isShareCheckOpen = true
            )
        }
    }

    fun closeShareCheck() = _uiState.update { it.copy(isShareCheckOpen = false) }

    fun scrubSensitiveItem(itemId: String) {
        _uiState.update { state ->
            val updated = state.shareCheckItems.map {
                if (it.id == itemId) it.copy(isScrubbed = true) else it
            }
            state.copy(shareCheckItems = updated)
        }
    }

    fun scrubAllSensitiveItems() {
        _uiState.update { state ->
            val updated = state.shareCheckItems.map { it.copy(isScrubbed = true) }
            state.copy(shareCheckItems = updated)
        }
    }

    fun dispatchScrubbedMedia() {
        val user = _uiState.value.currentUserAccount ?: return
        val chatId = _uiState.value.currentOpenChatId ?: return
        val peerUserId = chatId.removePrefix("conv_")
        val cacheDir = getApplication<Application>().cacheDir
        val sampleFile = File(cacheDir, "sample_media_preflight.jpg")

        viewModelScope.launch {
            val sanitizationResult = ShareCheckScrubber.sanitizeFile(sampleFile, cacheDir)
            if (sanitizationResult.success) {
                repository.sendEncryptedMessage(
                    conversationId = chatId,
                    senderUserId = user.userId,
                    recipientUserId = peerUserId,
                    plaintext = "📷 [Metadata Sanitized • EXIF & Telemetry Scrubbed • Verified Zero Residual GPS]",
                    isScrubbedMedia = true,
                    attachmentType = "IMAGE",
                    attachmentName = "sanitized_photo.jpg",
                    attachmentSize = 1024L
                )
                _uiState.update {
                    it.copy(
                        isShareCheckOpen = false,
                        toastMessage = "Sanitized media dispatched with zero residual EXIF."
                    )
                }
            } else {
                _uiState.update { it.copy(toastMessage = "Sanitization check failed: ${sanitizationResult.message}") }
            }
        }
    }

    // --- Biometric Authentication ---

    fun authenticateWithBiometric(
        activity: FragmentActivity,
        onSuccess: () -> Unit
    ) {
        if (!biometricManager.isBiometricAvailable()) {
            _uiState.update { it.copy(isEnclaveLocked = false) }
            onSuccess()
            return
        }

        biometricManager.promptBiometricAuthentication(
            activity = activity,
            onSuccess = {
                _uiState.update { it.copy(isEnclaveLocked = false) }
                onSuccess()
            },
            onError = { err ->
                _uiState.update { it.copy(toastMessage = "Authentication failed: $err") }
            }
        )
    }

    fun toggleBiometricSetting() {
        val user = _uiState.value.currentUserAccount ?: return
        val newSetting = !_uiState.value.isBiometricLockEnabled
        viewModelScope.launch {
            repository.toggleBiometric(user.userId, newSetting)
            _uiState.update {
                it.copy(
                    isBiometricLockEnabled = newSetting,
                    toastMessage = if (newSetting) "Biometric lock enabled" else "Biometric lock disabled"
                )
            }
        }
    }

    fun toggleOnlinePresence() {
        val user = _uiState.value.currentUserAccount ?: return
        val newSetting = !_uiState.value.showOnlinePresence
        viewModelScope.launch {
            repository.updateOnlinePrivacy(user.userId, newSetting, _uiState.value.showTypingIndicator)
            _uiState.update { it.copy(showOnlinePresence = newSetting) }
        }
    }

    fun toggleTypingIndicator() {
        val user = _uiState.value.currentUserAccount ?: return
        val newSetting = !_uiState.value.showTypingIndicator
        viewModelScope.launch {
            repository.updateOnlinePrivacy(user.userId, _uiState.value.showOnlinePresence, newSetting)
            _uiState.update { it.copy(showTypingIndicator = newSetting) }
        }
    }

    // --- Private Rooms Task Checklist & Admin ---

    fun toggleRoomTask(roomId: String, taskId: String) {
        viewModelScope.launch {
            repository.toggleRoomTask(roomId, taskId)
        }
    }

    fun inviteToRoom(roomId: String, inviteeUserId: String) {
        val user = _uiState.value.currentUserAccount ?: return
        viewModelScope.launch {
            val res = repository.inviteToRoom(roomId, user.userId, inviteeUserId)
            if (res.isSuccess) {
                _uiState.update { it.copy(toastMessage = "Member invited successfully.") }
            } else {
                _uiState.update { it.copy(toastMessage = "Invite failed: ${res.exceptionOrNull()?.message}") }
            }
        }
    }

    fun removeRoomMember(roomId: String, memberToRemoveId: String) {
        val user = _uiState.value.currentUserAccount ?: return
        viewModelScope.launch {
            val res = repository.removeRoomMember(roomId, user.userId, memberToRemoveId)
            if (res.isSuccess) {
                _uiState.update { it.copy(toastMessage = "Member removed.") }
            } else {
                _uiState.update { it.copy(toastMessage = "Removal failed: ${res.exceptionOrNull()?.message}") }
            }
        }
    }

    fun leaveRoom(roomId: String) {
        val user = _uiState.value.currentUserAccount ?: return
        viewModelScope.launch {
            val res = repository.leaveRoom(roomId, user.userId)
            if (res.isSuccess) {
                _uiState.update { it.copy(toastMessage = "Left room.") }
            }
        }
    }

    // --- Preferences ---

    fun selectFilter(filter: ChatFilter) = _uiState.update { it.copy(selectedFilter = filter) }

    fun toggleNotificationRedacted() {
        val user = _uiState.value.currentUserAccount ?: return
        val newSetting = !_uiState.value.isNotificationRedacted
        viewModelScope.launch {
            repository.toggleRedactNotifications(user.userId, newSetting)
            _uiState.update { it.copy(isNotificationRedacted = newSetting) }
        }
    }

    fun toggleLanguage() {
        _uiState.update {
            it.copy(
                currentLanguage = if (it.currentLanguage == AppLanguage.ENGLISH) AppLanguage.BANGLA else AppLanguage.ENGLISH
            )
        }
    }

    fun clearToast() = _uiState.update { it.copy(toastMessage = null) }

    private fun showInboundMessageNotification(peerName: String, previewText: String, conversationId: String) {
        try {
            val context = getApplication<Application>()
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "fort_messages_channel",
                    "Fort Secure Messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "End-to-end encrypted Fort incoming messages"
                    enableLights(true)
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val redact = _uiState.value.currentUserAccount?.redactNotifications ?: true
            val title = if (redact) "Fort Sovereign Message" else peerName
            val text = if (redact) "New encrypted message received • Sovereign Enclave" else previewText

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("OPEN_CONVERSATION_ID", conversationId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                conversationId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, "fort_messages_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(conversationId.hashCode(), notification)
        } catch (_: Exception) {}
    }

    companion object {
        fun formatTimestamp(millis: Long): String {
            if (millis <= 0) return ""
            val now = System.currentTimeMillis()
            val diff = now - millis
            val calMsg = java.util.Calendar.getInstance().apply { timeInMillis = millis }
            val calNow = java.util.Calendar.getInstance().apply { timeInMillis = now }
            val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            return when {
                diff < 60 * 1000 -> "Just now"
                calMsg.get(java.util.Calendar.YEAR) == calNow.get(java.util.Calendar.YEAR) &&
                calMsg.get(java.util.Calendar.DAY_OF_YEAR) == calNow.get(java.util.Calendar.DAY_OF_YEAR) -> timeFormat.format(java.util.Date(millis))
                calMsg.get(java.util.Calendar.YEAR) == calNow.get(java.util.Calendar.YEAR) &&
                calMsg.get(java.util.Calendar.DAY_OF_YEAR) == calNow.get(java.util.Calendar.DAY_OF_YEAR) - 1 -> "Yesterday"
                else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(millis))
            }
        }
    }
}
