package com.fort.messenger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fort.messenger.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppLanguage {
    ENGLISH,
    BANGLA
}

data class FortUiState(
    val connectionCards: List<ConnectionCard> = emptyList(),
    val activeCardId: String = "card_personal",
    val moodState: MoodRingState? = null,
    val conversations: List<ChatConversation> = emptyList(),
    val selectedFilter: com.fort.messenger.ui.components.ChatFilter = com.fort.messenger.ui.components.ChatFilter.ALL,
    val sharingCircles: List<SharingCircle> = emptyList(),
    val privateRooms: List<PrivateRoom> = emptyList(),
    val inboundRequests: List<KnockFirstRequest> = emptyList(),
    val activePasses: List<ContactPass> = emptyList(),
    val accessMapAudit: AccessMapAudit? = null,
    val currentOpenChatId: String? = null,
    val activeChatExpirySetting: String = "24 Hours",
    val shareCheckItems: List<ShareCheckItem> = emptyList(),
    // Modals
    val isMoodPickerOpen: Boolean = false,
    val isPassGeneratorOpen: Boolean = false,
    val isPrivacyCheckOpen: Boolean = false,
    val isShareCheckOpen: Boolean = false,
    // Preferences
    val isBiometricLockEnabled: Boolean = true,
    val isNotificationRedacted: Boolean = true,
    val currentLanguage: AppLanguage = AppLanguage.ENGLISH,
    val toastMessage: String? = null
)

class FortMainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FortUiState())
    val uiState: StateFlow<FortUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        val initialCards = listOf(
            ConnectionCard(
                id = "card_personal",
                type = CardType.PERSONAL,
                displayName = "Alex Vance",
                handle = "@alex.personal.fort",
                bio = "Personal circle only • Close friends & family",
                avatarEmoji = "🛡️",
                publicKeyFingerprint = "7F3A · 92C1 · BB88 · E041 · 3D79",
                moodRingVisible = true,
                businessHoursOnly = false,
                activePassCount = 5
            ),
            ConnectionCard(
                id = "card_work",
                type = CardType.WORK,
                displayName = "Alex Vance, Principal",
                handle = "@alex.vance.work",
                bio = "Architecture & Systems Design • Mon-Fri 9-5",
                avatarEmoji = "💼",
                publicKeyFingerprint = "1A88 · 42FE · C901 · 8820 · AA5F",
                moodRingVisible = false,
                businessHoursOnly = true,
                activePassCount = 3
            ),
            ConnectionCard(
                id = "card_travel",
                type = CardType.TRAVEL,
                displayName = "Alex (Transit)",
                handle = "@alex.nomad",
                bio = "Exploring Alps & Fjords • Ephemeral beacons",
                avatarEmoji = "🧭",
                publicKeyFingerprint = "CC31 · 77A9 · 012B · FE33 · 9981",
                moodRingVisible = true,
                businessHoursOnly = false,
                activePassCount = 2
            ),
            ConnectionCard(
                id = "card_market",
                type = CardType.MARKETPLACE,
                displayName = "AV_Seller402",
                handle = "@av.market.transact",
                bio = "Single-deal pass • Verified peer transactions",
                avatarEmoji = "🏷️",
                publicKeyFingerprint = "44B9 · D218 · 67E0 · 1139 · 85E2",
                moodRingVisible = false,
                businessHoursOnly = false,
                activePassCount = 1
            )
        )

        val initialMood = MoodRingState(
            emotion = MoodEmotion.NEED_QUIET,
            whatINeed = WhatINeed.GIVE_ME_SPACE,
            sharingCircleName = "Close Circle",
            duration = DecayDuration.HOURS_2,
            remainingTimeString = "1h 42m left"
        )

        val initialConversations = listOf(
            ChatConversation(
                id = "conv_1",
                participantName = "Maya Lin",
                handle = "@maya.close",
                avatarEmoji = "🌿",
                cardType = CardType.PERSONAL,
                lastMessage = "Sent the updated blueprints. Check when free!",
                lastMessageTime = "12:45 PM",
                unreadCount = 2,
                moodEmoji = "😊",
                moodWhatINeed = "Offer advice",
                passTimeRemaining = "6d 18h left",
                passType = PassDurationType.SEVEN_DAYS,
                messages = listOf(
                    ChatMessage("m1", "Maya Lin", "Hey Alex, are you available for a quick review?", "12:30 PM", false),
                    ChatMessage("m2", "Alex Vance", "Hey Maya! Taking some quiet focus time right now, but feel free to drop them here.", "12:38 PM", true),
                    ChatMessage("m3", "Maya Lin", "Sent the updated blueprints. Check when free!", "12:45 PM", false)
                )
            ),
            ChatConversation(
                id = "conv_2",
                participantName = "Julian Rowe",
                handle = "@jrowe.lead",
                avatarEmoji = "⚡",
                cardType = CardType.WORK,
                lastMessage = "Understood. We will sync during business hours tomorrow.",
                lastMessageTime = "11:20 AM",
                unreadCount = 0,
                moodEmoji = null,
                passTimeRemaining = "Ongoing",
                passType = PassDurationType.ONGOING,
                messages = listOf(
                    ChatMessage("m4", "Julian Rowe", "Regarding the contract deliverables for Q3...", "11:15 AM", false),
                    ChatMessage("m5", "Alex Vance", "Understood. We will sync during business hours tomorrow.", "11:20 AM", true)
                )
            ),
            ChatConversation(
                id = "conv_3",
                participantName = "Elena Rostova",
                handle = "@elena.trek",
                avatarEmoji = "🏔️",
                cardType = CardType.TRAVEL,
                lastMessage = "Trail marker #4 is washed out, use eastern ridge path.",
                lastMessageTime = "Yesterday",
                unreadCount = 1,
                moodEmoji = "💬",
                moodWhatINeed = "Distract me",
                passTimeRemaining = "18h left",
                passType = PassDurationType.SEVEN_DAYS,
                messages = listOf(
                    ChatMessage("m6", "Elena Rostova", "Trail marker #4 is washed out, use eastern ridge path.", "Yesterday", false)
                )
            ),
            ChatConversation(
                id = "conv_4",
                participantName = "Vintage Audio Gear",
                handle = "@audio.classifieds",
                avatarEmoji = "📻",
                cardType = CardType.MARKETPLACE,
                lastMessage = "Meetup confirmed for 5 PM at public transit hub.",
                lastMessageTime = "Oct 1",
                unreadCount = 0,
                moodEmoji = null,
                passTimeRemaining = "1 Convo",
                passType = PassDurationType.ONE_CONVERSATION,
                messages = listOf(
                    ChatMessage("m7", "Audio Seller", "Meetup confirmed for 5 PM at public transit hub.", "Oct 1", false)
                )
            )
        )

        val closeCircleMembers = listOf(
            CircleMember("m1", "Maya Lin", "🌿", CardType.PERSONAL, "😊"),
            CircleMember("m2", "Liam Keller", "🌲", CardType.PERSONAL, "🌙"),
            CircleMember("m3", "Sofia Chen", "🎨", CardType.PERSONAL, "💬")
        )

        val workCircleMembers = listOf(
            CircleMember("w1", "Julian Rowe", "⚡", CardType.WORK),
            CircleMember("w2", "Devon Park", "📐", CardType.WORK)
        )

        val sharingCircles = listOf(
            SharingCircle(
                id = "circle_close",
                name = "Close Circle",
                description = "Sees full Mood Ring and emotional availability broadcast.",
                iconEmoji = "✨",
                isMoodBroadcastEnabled = true,
                members = closeCircleMembers
            ),
            SharingCircle(
                id = "circle_work",
                name = "Work & Collaborators",
                description = "Business hours availability only. Mood status strictly hidden.",
                iconEmoji = "💼",
                isMoodBroadcastEnabled = false,
                members = workCircleMembers
            )
        )

        val privateRooms = listOf(
            PrivateRoom(
                id = "room_trek",
                name = "Weekend Mountain Trek",
                purpose = "Trail coordination, weather alerts, and gear checklist.",
                iconEmoji = "⛺",
                expiryRemainingString = "4 days left",
                autoCloseWarning = "Auto-closes 24h after itinerary completion.",
                members = closeCircleMembers,
                tasks = listOf(
                    RoomTask("t1", "Water purification tablets packed", true, "Alex"),
                    RoomTask("t2", "Offline Topo GPS maps downloaded", true, "Maya"),
                    RoomTask("t3", "Emergency Satellite Beacon check", false, "Liam")
                )
            ),
            PrivateRoom(
                id = "room_sublet",
                name = "Apartment Sublet Handover",
                purpose = "Key exchange and bounded inspection inventory.",
                iconEmoji = "🔑",
                expiryRemainingString = "18 hours left",
                autoCloseWarning = "Closes automatically upon lease sign-off.",
                members = listOf(
                    CircleMember("s1", "Landlord Rep", "🏢", CardType.MARKETPLACE)
                ),
                tasks = listOf(
                    RoomTask("t4", "Meter readings photographed", true, "Alex"),
                    RoomTask("t5", "Spare keys placed in security lockbox", false, "Landlord Rep")
                )
            )
        )

        val inboundRequests = listOf(
            KnockFirstRequest(
                id = "req_1",
                senderName = "Marcus Brody",
                senderCardType = CardType.PERSONAL,
                source = RequestSource.QR_CODE,
                rawMessageExcerpt = "Hey Alex! Connected via the cryptography symposium badge scan. Would love to compare notes on zero-knowledge identity primitives.",
                sandboxedLink = "https://sandbox.fort-protocol.net/paper/zk-bounds",
                timestamp = "10 min ago",
                isCallBlocked = true,
                isMediaBlocked = true
            ),
            KnockFirstRequest(
                id = "req_2",
                senderName = "Buyer_9918",
                senderCardType = CardType.MARKETPLACE,
                source = RequestSource.MARKETPLACE_PASS,
                rawMessageExcerpt = "Inquiring regarding the mechanical keyboard listing. Is local handover in downtown possible tomorrow afternoon?",
                sandboxedLink = null,
                timestamp = "45 min ago",
                isCallBlocked = true,
                isMediaBlocked = true
            )
        )

        val activePasses = listOf(
            ContactPass(
                id = "pass_1",
                token = "PASS-7D-8820-LIN",
                cardType = CardType.PERSONAL,
                durationType = PassDurationType.SEVEN_DAYS,
                status = PassStatus.ACTIVE,
                counterpartyName = "Maya Lin",
                timeRemainingString = "6d 18h left",
                expiryTimestamp = System.currentTimeMillis() + 580000000L
            ),
            ContactPass(
                id = "pass_2",
                token = "PASS-1C-44B9-AUDIO",
                cardType = CardType.MARKETPLACE,
                durationType = PassDurationType.ONE_CONVERSATION,
                status = PassStatus.ACTIVE,
                counterpartyName = "Vintage Audio Gear",
                timeRemainingString = "1 Convo left",
                expiryTimestamp = System.currentTimeMillis() + 86400000L
            ),
            ContactPass(
                id = "pass_3",
                token = "PASS-OG-1A88-JULIAN",
                cardType = CardType.WORK,
                durationType = PassDurationType.ONGOING,
                status = PassStatus.ACTIVE,
                counterpartyName = "Julian Rowe",
                timeRemainingString = "Ongoing (Manual Revoke)",
                expiryTimestamp = Long.MAX_VALUE
            )
        )

        val enclaveDevices = listOf(
            EnclaveDevice("d1", "Pixel 9 Pro (Titan M2)", "Hardware Keystore Level 3 (StrongBox)", "Enrolled Mar 2026", true),
            EnclaveDevice("d2", "ThinkPad Enclave Token", "FIDO2 / U2F Hardware Authenticator", "Enrolled Jan 2026", false)
        )

        val activePassLinks = listOf(
            ActivePassLink("l1", "Symposium Networking QR", CardType.PERSONAL, PassDurationType.SEVEN_DAYS, "2 days ago", 4),
            ActivePassLink("l2", "Marketplace Classifieds Link", CardType.MARKETPLACE, PassDurationType.ONE_CONVERSATION, "Yesterday", 1)
        )

        val vaultStatus = BackupVaultStatus(
            isEncrypted = true,
            algorithm = "XChaCha20-Poly1305 (256-bit)",
            lastBackupDate = "Today, 03:00 AM",
            seedPhraseVerified = true
        )

        val defaultShareCheckItems = listOf(
            ShareCheckItem("sc_1", SensitivityType.EXIF_GPS, "EXIF GPS: 37.7749° N, 122.4194° W (San Francisco, CA)", false),
            ShareCheckItem("sc_2", SensitivityType.PHONE_NUMBER, "Personal Phone Match: +1 (415) 555-0199", false)
        )

        _uiState.update {
            it.copy(
                connectionCards = initialCards,
                moodState = initialMood,
                conversations = initialConversations,
                sharingCircles = sharingCircles,
                privateRooms = privateRooms,
                inboundRequests = inboundRequests,
                activePasses = activePasses,
                accessMapAudit = AccessMapAudit(
                    activePassesCount = activePasses.size,
                    enclaveDevices = enclaveDevices,
                    activePassLinks = activePassLinks,
                    vaultStatus = vaultStatus
                ),
                shareCheckItems = defaultShareCheckItems
            )
        }
    }

    // Persona Card Management
    fun selectCard(cardId: String) {
        _uiState.update { it.copy(activeCardId = cardId) }
    }

    // Filter Chips
    fun selectFilter(filter: com.fort.messenger.ui.components.ChatFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    // Mood Ring & Quiet Presence
    fun openMoodPicker() {
        _uiState.update { it.copy(isMoodPickerOpen = true) }
    }

    fun closeMoodPicker() {
        _uiState.update { it.copy(isMoodPickerOpen = false) }
    }

    fun broadcastMood(
        emotion: MoodEmotion,
        whatINeed: WhatINeed?,
        circleName: String,
        duration: DecayDuration
    ) {
        val remaining = when (duration) {
            DecayDuration.MINUTES_30 -> "30m left"
            DecayDuration.HOURS_2 -> "2h 00m left"
            DecayDuration.END_OF_DAY -> "8h 00m left"
            DecayDuration.CUSTOM -> "12h 00m left"
        }
        val newState = MoodRingState(
            emotion = emotion,
            whatINeed = whatINeed,
            sharingCircleName = circleName,
            duration = duration,
            remainingTimeString = remaining,
            isActive = true
        )
        _uiState.update {
            it.copy(
                moodState = newState,
                isMoodPickerOpen = false,
                toastMessage = "Quiet Presence broadcasted to $circleName"
            )
        }
    }

    fun clearMoodRing() {
        _uiState.update {
            it.copy(
                moodState = null,
                isMoodPickerOpen = false,
                toastMessage = "Quiet Presence cleared"
            )
        }
    }

    // Pass Generator Modal
    fun openPassGenerator() {
        _uiState.update { it.copy(isPassGeneratorOpen = true) }
    }

    fun closePassGenerator() {
        _uiState.update { it.copy(isPassGeneratorOpen = false) }
    }

    fun generateNewPass(cardType: CardType, durationType: PassDurationType): ContactPass {
        val token = "PASS-${UUID.randomUUID().toString().take(8).uppercase()}"
        val remaining = when (durationType) {
            PassDurationType.ONE_CONVERSATION -> "1 Convo"
            PassDurationType.SEVEN_DAYS -> "7 Days"
            PassDurationType.CUSTOM_DURATION -> "Custom Expiry"
            PassDurationType.ONGOING -> "Ongoing"
        }
        val newPass = ContactPass(
            id = UUID.randomUUID().toString(),
            token = token,
            cardType = cardType,
            durationType = durationType,
            status = PassStatus.ACTIVE,
            counterpartyName = "Invited Peer",
            timeRemainingString = remaining,
            expiryTimestamp = System.currentTimeMillis() + 604800000L
        )
        _uiState.update { state ->
            val updated = state.activePasses + newPass
            state.copy(
                activePasses = updated,
                accessMapAudit = state.accessMapAudit?.copy(activePassesCount = updated.size),
                toastMessage = "Pass generated: $token"
            )
        }
        return newPass
    }

    fun revokePass(passId: String) {
        _uiState.update { state ->
            val updated = state.activePasses.filterNot { it.id == passId }
            state.copy(
                activePasses = updated,
                accessMapAudit = state.accessMapAudit?.copy(activePassesCount = updated.size),
                toastMessage = "Pass revoked. Future inbound transmissions terminated."
            )
        }
    }

    // Knock First Triage
    fun acceptRequestOnce(requestId: String) {
        val req = _uiState.value.inboundRequests.find { it.id == requestId } ?: return
        val newConv = ChatConversation(
            id = UUID.randomUUID().toString(),
            participantName = req.senderName,
            handle = "@${req.senderName.lowercase().replace(" ", "")}.fort",
            avatarEmoji = "🛡️",
            cardType = req.senderCardType,
            lastMessage = req.rawMessageExcerpt,
            lastMessageTime = "Just now",
            unreadCount = 0,
            passTimeRemaining = "1 Convo",
            passType = PassDurationType.ONE_CONVERSATION,
            messages = listOf(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    senderName = req.senderName,
                    text = req.rawMessageExcerpt,
                    timestamp = "Just now",
                    isMine = false
                )
            )
        )
        _uiState.update { state ->
            state.copy(
                inboundRequests = state.inboundRequests.filterNot { it.id == requestId },
                conversations = listOf(newConv) + state.conversations,
                toastMessage = "Accepted once. Single-conversation sandbox granted."
            )
        }
    }

    fun grantRequestSevenDays(requestId: String) {
        val req = _uiState.value.inboundRequests.find { it.id == requestId } ?: return
        val newConv = ChatConversation(
            id = UUID.randomUUID().toString(),
            participantName = req.senderName,
            handle = "@${req.senderName.lowercase().replace(" ", "")}.fort",
            avatarEmoji = "🛡️",
            cardType = req.senderCardType,
            lastMessage = req.rawMessageExcerpt,
            lastMessageTime = "Just now",
            unreadCount = 0,
            passTimeRemaining = "7d left",
            passType = PassDurationType.SEVEN_DAYS,
            messages = listOf(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    senderName = req.senderName,
                    text = req.rawMessageExcerpt,
                    timestamp = "Just now",
                    isMine = false
                )
            )
        )
        _uiState.update { state ->
            state.copy(
                inboundRequests = state.inboundRequests.filterNot { it.id == requestId },
                conversations = listOf(newConv) + state.conversations,
                toastMessage = "Granted 7-Day Contact Pass."
            )
        }
    }

    fun declineRequest(requestId: String) {
        _uiState.update { state ->
            state.copy(
                inboundRequests = state.inboundRequests.filterNot { it.id == requestId },
                toastMessage = "Request quietly dismissed without notification."
            )
        }
    }

    fun blockAndReportRequest(requestId: String) {
        _uiState.update { state ->
            state.copy(
                inboundRequests = state.inboundRequests.filterNot { it.id == requestId },
                toastMessage = "Entity blocked and cryptographic token blacklisted."
            )
        }
    }

    // Task Checklist in Private Rooms
    fun toggleRoomTask(roomId: String, taskId: String) {
        _uiState.update { state ->
            val updatedRooms = state.privateRooms.map { room ->
                if (room.id == roomId) {
                    val updatedTasks = room.tasks.map { task ->
                        if (task.id == taskId) task.copy(isCompleted = !task.isCompleted) else task
                    }
                    room.copy(tasks = updatedTasks)
                } else room
            }
            state.copy(privateRooms = updatedRooms)
        }
    }

    // Chat Navigation & Messaging
    fun openChat(conversationId: String) {
        _uiState.update { it.copy(currentOpenChatId = conversationId) }
    }

    fun closeChat() {
        _uiState.update { it.copy(currentOpenChatId = null) }
    }

    fun sendMessage(text: String) {
        val chatId = _uiState.value.currentOpenChatId ?: return
        if (text.isBlank()) return

        val newMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderName = "Alex Vance",
            text = text,
            timestamp = "Just now",
            isMine = true
        )

        _uiState.update { state ->
            val updatedConversations = state.conversations.map { conv ->
                if (conv.id == chatId) {
                    conv.copy(
                        lastMessage = text,
                        lastMessageTime = "Just now",
                        messages = conv.messages + newMessage
                    )
                } else conv
            }
            state.copy(conversations = updatedConversations)
        }
    }

    fun sendSupportResponse(targetEmoji: String = "🤍") {
        sendMessage("I'm here $targetEmoji")
    }

    fun setChatExpiry(setting: String) {
        _uiState.update { it.copy(activeChatExpirySetting = setting) }
    }

    // Privacy Check Modal
    fun openPrivacyCheck() {
        _uiState.update { it.copy(isPrivacyCheckOpen = true) }
    }

    fun closePrivacyCheck() {
        _uiState.update { it.copy(isPrivacyCheckOpen = false) }
    }

    // Share Check Modal (Media Scrubber)
    fun openShareCheck() {
        _uiState.update { it.copy(isShareCheckOpen = true) }
    }

    fun closeShareCheck() {
        _uiState.update { it.copy(isShareCheckOpen = false) }
    }

    fun scrubSensitiveItem(itemId: String) {
        _uiState.update { state ->
            val updatedItems = state.shareCheckItems.map {
                if (it.id == itemId) it.copy(isScrubbed = true) else it
            }
            state.copy(shareCheckItems = updatedItems)
        }
    }

    fun scrubAllSensitiveItems() {
        _uiState.update { state ->
            val updatedItems = state.shareCheckItems.map { it.copy(isScrubbed = true) }
            state.copy(shareCheckItems = updatedItems)
        }
    }

    fun dispatchScrubbedMedia() {
        val chatId = _uiState.value.currentOpenChatId ?: return
        val mediaMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderName = "Alex Vance",
            text = "📷 Attached Document / Image [Metadata Sanitized • EXIF & Personal Data Scrubbed]",
            timestamp = "Just now",
            isMine = true,
            isScrubbedMedia = true
        )
        _uiState.update { state ->
            val updatedConversations = state.conversations.map { conv ->
                if (conv.id == chatId) {
                    conv.copy(
                        lastMessage = "[Sanitized Photo Sent]",
                        lastMessageTime = "Just now",
                        messages = conv.messages + mediaMsg
                    )
                } else conv
            }
            state.copy(
                conversations = updatedConversations,
                isShareCheckOpen = false,
                toastMessage = "Media dispatched with all sensitive metadata stripped."
            )
        }
    }

    // Preferences
    fun toggleBiometricLock() {
        _uiState.update { it.copy(isBiometricLockEnabled = !it.isBiometricLockEnabled) }
    }

    fun toggleNotificationRedacted() {
        _uiState.update { it.copy(isNotificationRedacted = !it.isNotificationRedacted) }
    }

    fun toggleLanguage() {
        _uiState.update {
            it.copy(
                currentLanguage = if (it.currentLanguage == AppLanguage.ENGLISH) AppLanguage.BANGLA else AppLanguage.ENGLISH
            )
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
