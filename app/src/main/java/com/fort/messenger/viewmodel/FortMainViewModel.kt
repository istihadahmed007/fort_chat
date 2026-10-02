package com.fort.messenger.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fort.messenger.data.local.*
import com.fort.messenger.data.remote.InMemoryRemoteRelay
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class AppLanguage {
    ENGLISH,
    BANGLA
}

data class FortUiState(
    val currentUserAccount: UserAccountEntity? = null,
    val connectionCards: List<ConnectionCard> = emptyList(),
    val activeCardId: String = "",
    val moodState: MoodRingState? = null,
    val peerMoodStates: Map<String, MoodRingState> = emptyMap(), // peerUserId -> mood
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
    // Share Check State
    val inspectedFileReport: ScrubberReport? = null,
    val sanitizationResult: SanitizationResult? = null,
    val shareCheckItems: List<ShareCheckItem> = emptyList(),
    // Modals
    val isMoodPickerOpen: Boolean = false,
    val isPassGeneratorOpen: Boolean = false,
    val isPrivacyCheckOpen: Boolean = false,
    val isShareCheckOpen: Boolean = false,
    // Security & Preferences
    val isBiometricLockEnabled: Boolean = false,
    val isEnclaveLocked: Boolean = false,
    val isNotificationRedacted: Boolean = true,
    val currentLanguage: AppLanguage = AppLanguage.ENGLISH,
    val toastMessage: String? = null,
    val isDebugFixtureEnabled: Boolean = false
)

class FortMainViewModel(
    application: Application,
    val repository: FortRepository = FortRepository(
        database = FortDatabase.getInstance(application),
        remoteBackend = InMemoryRemoteRelay()
    )
) : AndroidViewModel(application) {

    private val biometricManager = FortBiometricManager(application)
    private val _uiState = MutableStateFlow(FortUiState())
    val uiState: StateFlow<FortUiState> = _uiState.asStateFlow()

    init {
        initializeAccountAndData()
    }

    private fun initializeAccountAndData() {
        viewModelScope.launch {
            // Check for existing account session in Room DB
            var account = repository.getActiveAccount().firstOrNull()
            if (account == null) {
                // Initialize default sovereign account
                val result = repository.register(
                    email = "alex.vance@fort-sovereign.net",
                    password = "SecureSovereignPassword123!",
                    displayName = "Alex Vance"
                )
                account = result.getOrNull()
            }

            if (account != null) {
                _uiState.update {
                    it.copy(
                        currentUserAccount = account,
                        activeCardId = account.activeCardId,
                        isBiometricLockEnabled = account.biometricEnabled,
                        isNotificationRedacted = account.redactNotifications
                    )
                }
                observeUserData(account.userId)
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

        // 2. Observe Active Mood Ring with real time-based decay check
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

        // 3. Observe Peer Connections & Build Conversation Feed
        viewModelScope.launch {
            repository.getActiveConnections(userId).collect { connections ->
                val convList = connections.map { conn ->
                    val passRemaining = if (conn.passExpiresAt == Long.MAX_VALUE) {
                        "Ongoing"
                    } else {
                        val hours = (conn.passExpiresAt - System.currentTimeMillis()) / (3600 * 1000)
                        if (hours > 24) "${hours / 24}d left" else "${maxOf(0L, hours)}h left"
                    }

                    // Query messages from Room DB for this conversation
                    val convId = "conv_${conn.peerUserId}"
                    val messages = repository.getConversationMessages(convId).firstOrNull() ?: emptyList()
                    val lastMsg = messages.lastOrNull()?.decryptedTextCache ?: "Pass established. E2EE ready."
                    val lastTime = messages.lastOrNull()?.let { "Just now" } ?: "Recent"

                    ChatConversation(
                        id = convId,
                        participantName = conn.peerDisplayName,
                        handle = conn.peerHandle,
                        avatarEmoji = conn.peerAvatarEmoji,
                        cardType = conn.peerCardType,
                        lastMessage = lastMsg,
                        lastMessageTime = lastTime,
                        unreadCount = 0,
                        moodEmoji = null,
                        moodWhatINeed = null,
                        passTimeRemaining = passRemaining,
                        passType = conn.passType,
                        isRoom = false,
                        messages = messages.map { m ->
                            ChatMessage(
                                id = m.messageId,
                                senderName = if (m.isMine) "You" else conn.peerDisplayName,
                                text = m.decryptedTextCache,
                                timestamp = "Just now",
                                isMine = m.isMine,
                                isScrubbedMedia = m.isScrubbedMedia
                            )
                        }
                    )
                }
                _uiState.update { it.copy(conversations = convList) }
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
    }

    // --- Authentication Actions ---

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            val result = repository.login(email, pass)
            if (result.isSuccess) {
                val acc = result.getOrThrow()
                _uiState.update { it.copy(currentUserAccount = acc, toastMessage = "Signed in as ${acc.email}") }
                observeUserData(acc.userId)
            } else {
                _uiState.update { it.copy(toastMessage = "Login failed: ${result.exceptionOrNull()?.message}") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _uiState.update {
                it.copy(
                    currentUserAccount = null,
                    conversations = emptyList(),
                    activePasses = emptyList(),
                    inboundRequests = emptyList(),
                    toastMessage = "Signed out"
                )
            }
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
            DecayDuration.END_OF_DAY -> -1L // Special flag for local day end
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

    // --- Contact Pass Generator Actions ---

    fun openPassGenerator() = _uiState.update { it.copy(isPassGeneratorOpen = true) }
    fun closePassGenerator() = _uiState.update { it.copy(isPassGeneratorOpen = false) }

    fun generateNewPass(cardType: CardType, durationType: PassDurationType): ContactPass? {
        val user = _uiState.value.currentUserAccount ?: return null
        var createdPass: ContactPass? = null
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
                createdPass = uiPass
                _uiState.update {
                    it.copy(
                        generatedPassQrBitmap = bitmap,
                        activeGeneratedPass = uiPass,
                        toastMessage = "Contact Pass generated: ${p.token}"
                    )
                }
            }
        }
        return createdPass
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

    // --- Messaging & E2EE ---

    fun openChat(conversationId: String) {
        val user = _uiState.value.currentUserAccount ?: return
        val peerUserId = conversationId.removePrefix("conv_")
        viewModelScope.launch {
            val connection = repository.getActiveConnections(user.userId).firstOrNull()?.find { it.peerUserId == peerUserId }
            _uiState.update {
                it.copy(
                    currentOpenChatId = conversationId,
                    currentPeerConnection = connection
                )
            }
        }
    }

    fun closeChat() = _uiState.update { it.copy(currentOpenChatId = null, currentPeerConnection = null) }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val user = _uiState.value.currentUserAccount ?: return
        val chatId = _uiState.value.currentOpenChatId ?: return
        val peerUserId = chatId.removePrefix("conv_")

        viewModelScope.launch {
            val result = repository.sendEncryptedMessage(
                conversationId = chatId,
                senderUserId = user.userId,
                recipientUserId = peerUserId,
                plaintext = text,
                isScrubbedMedia = false
            )
            if (result.isFailure) {
                _uiState.update { it.copy(toastMessage = "Delivery failed: ${result.exceptionOrNull()?.message}") }
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
        // Inspect a real dummy image in app's internal cache for testing
        val cacheDir = getApplication<Application>().cacheDir
        val sampleFile = File(cacheDir, "sample_media_preflight.jpg")
        if (!sampleFile.exists()) {
            sampleFile.writeBytes(ByteArray(1024)) // 1KB sample payload
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
                    isScrubbedMedia = true
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

    // --- Private Rooms Task Checklist ---

    fun toggleRoomTask(roomId: String, taskId: String) {
        viewModelScope.launch {
            repository.toggleRoomTask(roomId, taskId)
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
}
