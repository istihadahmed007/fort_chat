package com.fort.messenger.data.repository

import android.app.Activity

import com.fort.messenger.data.local.*
import com.fort.messenger.data.remote.*
import com.fort.messenger.model.*
import com.fort.messenger.security.FortCryptoManager
import com.fort.messenger.security.IdentityKeyPair
import com.fort.messenger.security.KeyStoreMaster
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class FortRepository(
    private val database: FortDatabase,
    private val remoteBackend: FortRemoteBackend,
    private val keyStoreMaster: KeyStoreMaster = KeyStoreMaster()
) {

    // --- Authentication & Session Management ---

    fun getActiveAccount(): Flow<UserAccountEntity?> = database.userAccountDao().getActiveAccount()

    private suspend fun initLocalUserAccount(remoteUser: RemoteUserAccount, displayName: String): Result<UserAccountEntity> {
        // Generate distinct, isolated identity key pairs for each persona facet (Zero-Correlation Identity)
        val personalKey = FortCryptoManager.generateIdentityKeyPair()
        val workKey = FortCryptoManager.generateIdentityKeyPair()
        val travelKey = FortCryptoManager.generateIdentityKeyPair()
        val marketKey = FortCryptoManager.generateIdentityKeyPair()

        // Publish public keys to server
        remoteBackend.publishPublicKey(remoteUser.userId, CardType.PERSONAL.name, personalKey.publicKeyBase64)
        remoteBackend.publishPublicKey(remoteUser.userId, CardType.WORK.name, workKey.publicKeyBase64)
        remoteBackend.publishPublicKey(remoteUser.userId, CardType.TRAVEL.name, travelKey.publicKeyBase64)
        remoteBackend.publishPublicKey(remoteUser.userId, CardType.MARKETPLACE.name, marketKey.publicKeyBase64)

        // Protect private keys at rest via Android Keystore master key (fail-closed AES-256-GCM)
        val encPersonalPriv = keyStoreMaster.encryptLocalData(personalKey.privateKeyBase64)
        val encWorkPriv = keyStoreMaster.encryptLocalData(workKey.privateKeyBase64)
        val encTravelPriv = keyStoreMaster.encryptLocalData(travelKey.privateKeyBase64)
        val encMarketPriv = keyStoreMaster.encryptLocalData(marketKey.privateKeyBase64)

        val activeCardId = "card_${remoteUser.userId}_personal"
        val effectiveName = displayName.ifBlank {
            remoteUser.displayName.ifBlank {
                remoteUser.email.substringBefore("@").replaceFirstChar { it.uppercase() }
            }
        }
        val account = UserAccountEntity(
            userId = remoteUser.userId,
            email = remoteUser.email,
            displayName = effectiveName,
            phoneNumber = remoteUser.phoneNumber,
            authToken = "tok_${UUID.randomUUID()}",
            activeCardId = activeCardId,
            biometricEnabled = false,
            redactNotifications = true,
            showOnlinePresence = true,
            showTypingIndicator = true
        )
        database.userAccountDao().insertAccount(account)

        val cards = listOf(
            PersonaCardEntity(
                cardId = activeCardId,
                userId = remoteUser.userId,
                type = CardType.PERSONAL,
                displayName = effectiveName,
                handle = "@${effectiveName.lowercase().replace(" ", "")}.personal",
                bio = "Personal circle only • Close verified contacts",
                avatarEmoji = "🛡️",
                publicKey = personalKey.publicKeyBase64,
                privateKeyEncrypted = encPersonalPriv,
                businessHoursOnly = false,
                moodSharingEnabled = true
            ),
            PersonaCardEntity(
                cardId = "card_${remoteUser.userId}_work",
                userId = remoteUser.userId,
                type = CardType.WORK,
                displayName = "$effectiveName, Professional",
                handle = "@${effectiveName.lowercase().replace(" ", "")}.work",
                bio = "Professional engagements • Business hours only",
                avatarEmoji = "💼",
                publicKey = workKey.publicKeyBase64,
                privateKeyEncrypted = encWorkPriv,
                businessHoursOnly = true,
                moodSharingEnabled = false
            ),
            PersonaCardEntity(
                cardId = "card_${remoteUser.userId}_travel",
                userId = remoteUser.userId,
                type = CardType.TRAVEL,
                displayName = "$effectiveName (Transit)",
                handle = "@${effectiveName.lowercase().replace(" ", "")}.travel",
                bio = "Expedition & transit beacons • Ephemeral passes",
                avatarEmoji = "🧭",
                publicKey = travelKey.publicKeyBase64,
                privateKeyEncrypted = encTravelPriv,
                businessHoursOnly = false,
                moodSharingEnabled = true
            ),
            PersonaCardEntity(
                cardId = "card_${remoteUser.userId}_market",
                userId = remoteUser.userId,
                type = CardType.MARKETPLACE,
                displayName = "Trader_${remoteUser.userId.takeLast(4)}",
                handle = "@market.${remoteUser.userId.takeLast(4)}",
                bio = "Single-deal pass • Verified peer transactions",
                avatarEmoji = "🏷️",
                publicKey = marketKey.publicKeyBase64,
                privateKeyEncrypted = encMarketPriv,
                businessHoursOnly = false,
                moodSharingEnabled = false
            )
        )
        database.personaCardDao().insertCards(cards)
        cards.forEach { card ->
            remoteBackend.publishPublicKey(remoteUser.userId, card.type.name, card.publicKey)
        }
        return Result.success(account)
    }

    private suspend fun restoreOrInitLocalAccount(remoteUser: RemoteUserAccount, displayName: String): Result<UserAccountEntity> {
        val existing = database.userAccountDao().getActiveAccountOnce()
        if (existing != null && existing.userId == remoteUser.userId) {
            return Result.success(existing)
        }
        val existingCards = database.personaCardDao().getCardsForUserOnce(remoteUser.userId)
        if (existingCards.isNotEmpty()) {
            val activeCardId = "card_${remoteUser.userId}_personal"
            val effectiveName = displayName.ifBlank {
                remoteUser.displayName.ifBlank {
                    remoteUser.email.substringBefore("@").replaceFirstChar { it.uppercase() }
                }
            }
            val restoredAccount = UserAccountEntity(
                userId = remoteUser.userId,
                email = remoteUser.email,
                displayName = effectiveName,
                phoneNumber = remoteUser.phoneNumber,
                authToken = "tok_${UUID.randomUUID()}",
                activeCardId = activeCardId,
                biometricEnabled = false,
                redactNotifications = true,
                showOnlinePresence = true,
                showTypingIndicator = true
            )
            database.userAccountDao().insertAccount(restoredAccount)
            return Result.success(restoredAccount)
        }
        return initLocalUserAccount(remoteUser, displayName)
    }

    suspend fun register(email: String, password: String, displayName: String): Result<UserAccountEntity> {
        val remoteResult = remoteBackend.register(email, password, displayName)
        if (remoteResult.isFailure) {
            return Result.failure(remoteResult.exceptionOrNull()!!)
        }
        val remoteUser = remoteResult.getOrThrow()
        return initLocalUserAccount(remoteUser, displayName)
    }

    suspend fun login(email: String, password: String): Result<UserAccountEntity> {
        val remoteResult = remoteBackend.login(email, password)
        if (remoteResult.isFailure) return Result.failure(remoteResult.exceptionOrNull()!!)
        val remoteUser = remoteResult.getOrThrow()
        return restoreOrInitLocalAccount(remoteUser, remoteUser.displayName)
    }

    suspend fun sendPhoneOtp(phoneNumber: String, activity: Activity? = null): Result<String> {
        return remoteBackend.sendPhoneOtp(phoneNumber, activity)
    }

    suspend fun verifyPhoneOtp(verificationId: String, code: String, displayName: String): Result<UserAccountEntity> {
        val result = remoteBackend.verifyPhoneOtp(verificationId, code, displayName)
        if (result.isFailure) return Result.failure(result.exceptionOrNull()!!)
        val remoteUser = result.getOrThrow()
        return restoreOrInitLocalAccount(remoteUser, displayName.ifBlank { "Phone User ${remoteUser.phoneNumber?.takeLast(4) ?: "Sovereign"}" })
    }

    suspend fun loginWithGoogle(idToken: String, displayName: String): Result<UserAccountEntity> {
        val result = remoteBackend.loginWithGoogle(idToken, displayName)
        if (result.isFailure) return Result.failure(result.exceptionOrNull()!!)
        val remoteUser = result.getOrThrow()
        return restoreOrInitLocalAccount(remoteUser, displayName.ifBlank { "Google User" })
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return remoteBackend.sendPasswordReset(email)
    }

    suspend fun logout() {
        remoteBackend.signOut()
        database.userAccountDao().clearAccount()
    }

    fun getPersonaCards(userId: String): Flow<List<PersonaCardEntity>> =
        database.personaCardDao().getCardsForUser(userId)

    suspend fun selectActiveCard(userId: String, cardId: String) {
        database.userAccountDao().updateActiveCard(userId, cardId)
    }

    suspend fun toggleBiometric(userId: String, enabled: Boolean) {
        database.userAccountDao().updateBiometric(userId, enabled)
    }

    suspend fun toggleRedactNotifications(userId: String, redact: Boolean) {
        database.userAccountDao().updateRedactNotifications(userId, redact)
    }

    suspend fun updateOnlinePrivacy(userId: String, showOnline: Boolean, showTyping: Boolean) {
        database.userAccountDao().updateOnlinePrivacy(userId, showOnline, showTyping)
    }

    // --- Peer Connections & Safety Numbers ---

    fun getActiveConnections(userId: String): Flow<List<PeerConnectionEntity>> =
        database.peerConnectionDao().getActiveConnections(userId)

    suspend fun verifySafetyNumber(connectionId: String) {
        database.peerConnectionDao().verifySafetyNumber(connectionId)
    }

    suspend fun blockConnection(connectionId: String, currentUserId: String, peerUserId: String) {
        database.peerConnectionDao().blockConnection(connectionId)
        remoteBackend.blockUser(currentUserId, peerUserId)
    }

    suspend fun updatePeerTyping(connectionId: String, isTyping: Boolean) {
        database.peerConnectionDao().updatePeerTyping(connectionId, isTyping)
    }

    // --- Knock First Protocol ---

    fun getPendingRequests(userId: String): Flow<List<KnockFirstRequestEntity>> =
        database.knockFirstDao().getPendingRequests(userId)

    suspend fun submitKnockFirstRequest(
        recipientUserId: String,
        senderUserId: String,
        senderDisplayName: String,
        senderCardType: CardType,
        source: String,
        rawMessage: String,
        sandboxedLink: String? = null
    ): Result<Unit> {
        if (remoteBackend.isBlocked(senderUserId, recipientUserId)) {
            return Result.failure(SecurityException("Inbound transmission denied. Sender is blocked."))
        }
        val request = KnockFirstRequestEntity(
            requestId = "req_${UUID.randomUUID()}",
            recipientUserId = recipientUserId,
            senderUserId = senderUserId,
            senderDisplayName = senderDisplayName,
            senderCardType = senderCardType,
            source = source,
            rawMessage = rawMessage,
            sandboxedLink = sandboxedLink,
            timestamp = "Just now",
            status = "PENDING"
        )
        val remoteRes = remoteBackend.submitKnockFirstRequest(request)
        if (remoteRes.isFailure) return remoteRes

        database.knockFirstDao().insertRequest(request)
        return Result.success(Unit)
    }

    suspend fun syncInboundKnockFirstRequests(recipientUserId: String): Result<Int> {
        val remoteRes = remoteBackend.fetchKnockFirstRequests(recipientUserId)
        if (remoteRes.isFailure) return Result.failure(remoteRes.exceptionOrNull()!!)
        val remoteList = remoteRes.getOrThrow()
        var newCount = 0
        for (req in remoteList) {
            database.knockFirstDao().insertRequest(req)
            newCount++
        }
        return Result.success(newCount)
    }

    fun listenToInboundKnockFirstRequests(recipientUserId: String): Flow<List<KnockFirstRequestEntity>> {
        return remoteBackend.listenToInboundKnockFirstRequests(recipientUserId)
    }

    fun listenToOutboundKnockFirstRequests(senderUserId: String): Flow<List<KnockFirstRequestEntity>> {
        return remoteBackend.listenToOutboundKnockFirstRequests(senderUserId)
    }

    suspend fun handleOutboundKnockStatusChange(request: KnockFirstRequestEntity, currentUserId: String) {
        database.knockFirstDao().insertRequest(request)
        if (request.status == "ACCEPTED") {
            val existing = database.peerConnectionDao().getConnectionWithPeer(currentUserId, request.recipientUserId)
            if (existing == null) {
                val peerPubKey = remoteBackend.fetchPublicKey(request.recipientUserId, CardType.PERSONAL.name).getOrDefault("")
                val userCard = database.personaCardDao().getCardById(
                    database.userAccountDao().getActiveAccountOnce()?.activeCardId ?: ""
                )
                val safetyNumber = if (userCard != null && peerPubKey.isNotEmpty()) {
                    FortCryptoManager.computeSafetyNumber(userCard.publicKey, peerPubKey)
                } else "Pending Key Verification"

                val peerProfile = remoteBackend.fetchUserProfile(request.recipientUserId).getOrNull()
                val peerName = peerProfile?.displayName ?: "Sovereign Peer"

                val connection = PeerConnectionEntity(
                    connectionId = "conn_${UUID.randomUUID()}",
                    userId = currentUserId,
                    peerUserId = request.recipientUserId,
                    peerDisplayName = peerName,
                    peerHandle = peerProfile?.fortId ?: "@peer.${request.recipientUserId.takeLast(6)}",
                    peerCardType = CardType.PERSONAL,
                    peerPublicKey = peerPubKey,
                    safetyNumber = safetyNumber,
                    isVerified = false,
                    passType = PassDurationType.SEVEN_DAYS,
                    passExpiresAt = System.currentTimeMillis() + 7 * 86400000L,
                    status = "ACTIVE"
                )
                database.peerConnectionDao().insertConnection(connection)
            }
        }
    }

    suspend fun acceptRequestOnce(request: KnockFirstRequestEntity, currentUserId: String): Result<Unit> {
        database.knockFirstDao().updateRequestStatus(request.requestId, "ACCEPTED")
        remoteBackend.updateKnockFirstStatus(request.requestId, "ACCEPTED", currentUserId)

        val peerKeyResult = remoteBackend.fetchPublicKey(request.senderUserId, request.senderCardType.name)
        val peerPublicKey = peerKeyResult.getOrDefault("")

        val userCard = database.personaCardDao().getCardById(
            database.userAccountDao().getActiveAccountOnce()?.activeCardId ?: ""
        )
        val safetyNumber = if (userCard != null && peerPublicKey.isNotEmpty()) {
            FortCryptoManager.computeSafetyNumber(userCard.publicKey, peerPublicKey)
        } else "Pending Key Verification"

        val connection = PeerConnectionEntity(
            connectionId = "conn_${UUID.randomUUID()}",
            userId = currentUserId,
            peerUserId = request.senderUserId,
            peerDisplayName = request.senderDisplayName,
            peerHandle = "@${request.senderDisplayName.lowercase().replace(" ", "")}.fort",
            peerCardType = request.senderCardType,
            peerPublicKey = peerPublicKey,
            safetyNumber = safetyNumber,
            isVerified = false,
            passType = PassDurationType.ONE_CONVERSATION,
            passExpiresAt = System.currentTimeMillis() + 86400000L,
            status = "ACTIVE"
        )
        database.peerConnectionDao().insertConnection(connection)
        // Transmit encrypted reciprocal greeting to establish conversation on sender's device too
        sendEncryptedMessage(
            conversationId = "conv_${request.senderUserId}",
            senderUserId = currentUserId,
            recipientUserId = request.senderUserId,
            plaintext = "🤝 Knock First accepted. Sovereign channel connected."
        )
        return Result.success(Unit)
    }

    suspend fun grantRequestSevenDays(request: KnockFirstRequestEntity, currentUserId: String): Result<Unit> {
        database.knockFirstDao().updateRequestStatus(request.requestId, "ACCEPTED")
        remoteBackend.updateKnockFirstStatus(request.requestId, "ACCEPTED", currentUserId)

        val peerKeyResult = remoteBackend.fetchPublicKey(request.senderUserId, request.senderCardType.name)
        val peerPublicKey = peerKeyResult.getOrDefault("")

        val userCard = database.personaCardDao().getCardById(
            database.userAccountDao().getActiveAccountOnce()?.activeCardId ?: ""
        )
        val safetyNumber = if (userCard != null && peerPublicKey.isNotEmpty()) {
            FortCryptoManager.computeSafetyNumber(userCard.publicKey, peerPublicKey)
        } else "Pending Key Verification"

        val connection = PeerConnectionEntity(
            connectionId = "conn_${UUID.randomUUID()}",
            userId = currentUserId,
            peerUserId = request.senderUserId,
            peerDisplayName = request.senderDisplayName,
            peerHandle = "@${request.senderDisplayName.lowercase().replace(" ", "")}.fort",
            peerCardType = request.senderCardType,
            peerPublicKey = peerPublicKey,
            safetyNumber = safetyNumber,
            isVerified = false,
            passType = PassDurationType.SEVEN_DAYS,
            passExpiresAt = System.currentTimeMillis() + 7 * 86400000L,
            status = "ACTIVE"
        )
        database.peerConnectionDao().insertConnection(connection)
        // Transmit encrypted reciprocal greeting to establish conversation on sender's device too
        sendEncryptedMessage(
            conversationId = "conv_${request.senderUserId}",
            senderUserId = currentUserId,
            recipientUserId = request.senderUserId,
            plaintext = "🤝 Granted 7-Day Contact Pass. Sovereign channel connected."
        )
        return Result.success(Unit)
    }

    suspend fun declineRequest(requestId: String, currentUserId: String = "") {
        database.knockFirstDao().updateRequestStatus(requestId, "DECLINED")
        if (currentUserId.isNotBlank()) {
            remoteBackend.updateKnockFirstStatus(requestId, "DECLINED", currentUserId)
        }
    }

    suspend fun blockAndReportRequest(request: KnockFirstRequestEntity, currentUserId: String) {
        database.knockFirstDao().updateRequestStatus(request.requestId, "BLOCKED")
        remoteBackend.updateKnockFirstStatus(request.requestId, "BLOCKED", currentUserId)
        remoteBackend.blockUser(currentUserId, request.senderUserId)
    }

    // --- Contact Passes ---

    fun getActivePasses(userId: String): Flow<List<ContactPassEntity>> =
        database.contactPassDao().getActivePasses(userId)

    suspend fun generatePass(
        issuerUserId: String,
        cardType: CardType,
        durationType: PassDurationType,
        customExpiryMillis: Long? = null
    ): Result<ContactPassEntity> {
        val userCard = database.personaCardDao().getCardsForUser(issuerUserId).firstOrNull()?.find { it.type == cardType }
            ?: return Result.failure(IllegalStateException("No persona card found for type $cardType"))

        val now = System.currentTimeMillis()
        val expiresAt = when (durationType) {
            PassDurationType.ONE_CONVERSATION -> now + 86400000L
            PassDurationType.SEVEN_DAYS -> now + 7 * 86400000L
            PassDurationType.CUSTOM_DURATION -> customExpiryMillis ?: (now + 14 * 86400000L)
            PassDurationType.ONGOING -> Long.MAX_VALUE
        }
        val isSingleUse = durationType == PassDurationType.ONE_CONVERSATION

        val token = "PASS-${UUID.randomUUID().toString().take(8).uppercase()}"
        val passId = "pass_${UUID.randomUUID()}"

        val remoteRecord = RemotePassRecord(
            passId = passId,
            issuerUserId = issuerUserId,
            token = token,
            cardType = cardType.name,
            durationType = durationType.name,
            expiresAt = expiresAt,
            isSingleUse = isSingleUse,
            issuerPublicKey = userCard.publicKey
        )
        val publishResult = remoteBackend.publishPass(remoteRecord)
        if (publishResult.isFailure) {
            return Result.failure(
                publishResult.exceptionOrNull()
                    ?: IllegalStateException("Failed to publish contact pass remotely.")
            )
        }

        val localEntity = ContactPassEntity(
            passId = passId,
            issuerUserId = issuerUserId,
            token = token,
            cardType = cardType,
            durationType = durationType,
            createdAt = now,
            expiresAt = expiresAt,
            isSingleUse = isSingleUse,
            isClaimed = false,
            isRevoked = false
        )
        database.contactPassDao().insertPass(localEntity)
        return Result.success(localEntity)
    }

    suspend fun claimPass(
        token: String,
        claimantUserId: String,
        claimantDisplayName: String,
        passId: String? = null,
        issuerDisplayName: String? = null
    ): Result<PeerConnectionEntity> {
        val claimResult = remoteBackend.claimPass(token, claimantUserId, passId)
        if (claimResult.isFailure) return Result.failure(claimResult.exceptionOrNull()!!)
        val remotePass = claimResult.getOrThrow()

        val claimantCard = database.personaCardDao().getCardsForUser(claimantUserId).firstOrNull()?.firstOrNull()
            ?: return Result.failure(IllegalStateException("Claimant has no active persona card."))

        val safetyNumber = FortCryptoManager.computeSafetyNumber(
            claimantCard.publicKey,
            remotePass.issuerPublicKey
        )

        val passDurationType = try {
            PassDurationType.valueOf(remotePass.durationType)
        } catch (e: Exception) {
            PassDurationType.SEVEN_DAYS
        }

        val cardType = try {
            CardType.valueOf(remotePass.cardType)
        } catch (e: Exception) {
            CardType.PERSONAL
        }

        val effectiveDisplayName = issuerDisplayName?.takeIf { it.isNotBlank() }
            ?: remoteBackend.fetchUserProfile(remotePass.issuerUserId).getOrNull()?.displayName
            ?: "Pass Peer (${remotePass.cardType})"

        val connection = PeerConnectionEntity(
            connectionId = "conn_${UUID.randomUUID()}",
            userId = claimantUserId,
            peerUserId = remotePass.issuerUserId,
            peerDisplayName = effectiveDisplayName,
            peerHandle = "@${effectiveDisplayName.lowercase().replace(" ", "")}.fort",
            peerCardType = cardType,
            peerPublicKey = remotePass.issuerPublicKey,
            safetyNumber = safetyNumber,
            isVerified = false,
            passType = passDurationType,
            passExpiresAt = remotePass.expiresAt,
            status = "ACTIVE"
        )
        database.peerConnectionDao().insertConnection(connection)
        return Result.success(connection)
    }

    suspend fun markConversationAsRead(conversationId: String, currentUserId: String) {
        val unreadMessages = database.chatMessageDao().getMessagesForConversation(conversationId).firstOrNull()
            ?.filter { !it.isMine && it.deliveryStatus != "READ" } ?: emptyList()
        val count = database.chatMessageDao().markMessagesAsReadForConversation(conversationId)
        if (count > 0) {
            for (msg in unreadMessages) {
                remoteBackend.updateDeliveryStatus(msg.messageId, "READ", currentUserId)
            }
        }
    }

    suspend fun setTypingStatus(userId: String, recipientUserId: String, isTyping: Boolean) {
        remoteBackend.setTypingStatus(userId, recipientUserId, isTyping)
    }

    fun listenToPeerTyping(currentUserId: String, peerUserId: String): Flow<Boolean> {
        return remoteBackend.listenToTypingStatus(currentUserId, peerUserId)
    }

    suspend fun revokePass(passId: String, currentUserId: String): Result<Unit> {
        database.contactPassDao().revokePass(passId)
        return remoteBackend.revokePass(passId, currentUserId)
    }

    // --- End-to-End Encrypted Messaging ---

    fun getConversationMessages(conversationId: String): Flow<List<ChatMessageEntity>> =
        database.chatMessageDao().getMessagesForConversation(conversationId)

    suspend fun getMessageById(messageId: String): ChatMessageEntity? =
        database.chatMessageDao().getMessageById(messageId)

    suspend fun sendEncryptedMessage(
        conversationId: String,
        senderUserId: String,
        recipientUserId: String,
        plaintext: String,
        isScrubbedMedia: Boolean = false,
        replyToMessageId: String? = null,
        replyToSenderName: String? = null,
        replyToText: String? = null,
        attachmentUri: String? = null,
        attachmentType: String? = null,
        attachmentName: String? = null,
        attachmentSize: Long = 0L
    ): Result<ChatMessageEntity> {
        val senderAccount = database.userAccountDao().getActiveAccountOnce()
            ?: return Result.failure(IllegalStateException("No active account."))
        val senderCard = database.personaCardDao().getCardById(senderAccount.activeCardId)
            ?: return Result.failure(IllegalStateException("No active persona card."))

        val decryptedPriv = try {
            keyStoreMaster.decryptLocalData(senderCard.privateKeyEncrypted)
        } catch (e: Exception) {
            senderCard.privateKeyEncrypted
        }

        val senderKeyPair = IdentityKeyPair(
            publicKeyBase64 = senderCard.publicKey,
            privateKeyBase64 = decryptedPriv,
            fingerprint = FortCryptoManager.computeFingerprint(android.util.Base64.decode(senderCard.publicKey, android.util.Base64.NO_WRAP))
        )

        // Retrieve recipient public key matching the recipient's card type
        val connection = database.peerConnectionDao().getConnectionWithPeer(senderUserId, recipientUserId)
        val recipientCardType = connection?.peerCardType?.name ?: CardType.PERSONAL.name

        val recipientKeyResult = remoteBackend.fetchPublicKey(recipientUserId, recipientCardType)
        if (recipientKeyResult.isFailure) {
            return Result.failure(recipientKeyResult.exceptionOrNull()!!)
        }
        val recipientPublicKey = recipientKeyResult.getOrThrow()

        // Check for peer key rotation
        if (connection != null && connection.peerPublicKey.isNotEmpty() && connection.peerPublicKey != recipientPublicKey) {
            database.peerConnectionDao().reportKeyRotation(connection.connectionId, recipientPublicKey)
        }

        // Encrypt with ECDH + HKDF + AES-256-GCM and sign with sender ECDSA key
        val payload = FortCryptoManager.encrypt(
            plaintext = plaintext,
            recipientPublicKeyBase64 = recipientPublicKey,
            senderKeyPair = senderKeyPair
        )

        val messageId = "msg_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()

        // Transmit only ciphertext payload and sender signature to server relay, with persona card type metadata
        val packet = RemoteEncryptedPacket(
            packetId = messageId,
            senderUserId = senderUserId,
            recipientUserId = recipientUserId,
            ciphertextBase64 = payload.ciphertextBase64,
            ivBase64 = payload.ivBase64,
            ephemeralKeyBase64 = payload.ephemeralPublicKeyBase64,
            senderSignatureBase64 = payload.senderSignatureBase64,
            timestamp = now,
            senderCardType = senderCard.type.name,
            recipientCardType = recipientCardType
        )

        val transmitResult = remoteBackend.sendEncryptedPacket(packet)
        val initialStatus = if (transmitResult.isSuccess) "SENT" else "PENDING"

        // Protect message body at rest using local Keystore master key
        val encLocalText = keyStoreMaster.encryptLocalData(plaintext)

        val localEntity = ChatMessageEntity(
            messageId = messageId,
            conversationId = conversationId,
            senderUserId = senderUserId,
            recipientUserId = recipientUserId,
            ciphertext = payload.ciphertextBase64,
            iv = payload.ivBase64,
            ephemeralKey = payload.ephemeralPublicKeyBase64,
            senderSignature = payload.senderSignatureBase64,
            encryptedLocalPayload = encLocalText,
            decryptedTextCache = plaintext, // In-memory/legacy cache
            timestamp = now,
            isMine = true,
            isScrubbedMedia = isScrubbedMedia,
            deliveryStatus = initialStatus,
            replyToMessageId = replyToMessageId,
            replyToSenderName = replyToSenderName,
            replyToText = replyToText,
            reactionsJson = "{}",
            attachmentUri = attachmentUri,
            attachmentType = attachmentType,
            attachmentName = attachmentName,
            attachmentSize = attachmentSize
        )
        database.chatMessageDao().insertMessage(localEntity)

        return if (transmitResult.isSuccess) Result.success(localEntity) else Result.failure(transmitResult.exceptionOrNull() ?: Exception("Queued offline"))
    }

    fun listenToInboundPackets(recipientUserId: String): Flow<List<RemoteEncryptedPacket>> {
        return remoteBackend.listenToInboundPackets(recipientUserId)
    }

    suspend fun processInboundPackets(packets: List<RemoteEncryptedPacket>, currentUserId: String): Int {
        var decryptedCount = 0
        val allCards = database.personaCardDao().getCardsForUserOnce(currentUserId)
        if (allCards.isEmpty()) return 0

        for (packet in packets) {
            // Idempotency: skip if already in local Room database
            if (database.chatMessageDao().getMessageById(packet.packetId) != null) {
                continue
            }

            try {
                // Select matching recipient card for ECDH decryption based on packet metadata
                val recipientCard = allCards.find { it.type.name.equals(packet.recipientCardType, ignoreCase = true) }
                    ?: allCards.find { it.type == CardType.PERSONAL }
                    ?: allCards.first()

                val decryptedPriv = try {
                    keyStoreMaster.decryptLocalData(recipientCard.privateKeyEncrypted)
                } catch (e: Exception) {
                    recipientCard.privateKeyEncrypted
                }

                // Fetch sender public key for the specific sender card type
                val senderCardTypeStr = packet.senderCardType.ifBlank { CardType.PERSONAL.name }
                val senderConn = database.peerConnectionDao().getConnectionWithPeer(currentUserId, packet.senderUserId)
                val senderPubKey = senderConn?.peerPublicKey?.takeIf { it.isNotBlank() }
                    ?: remoteBackend.fetchPublicKey(packet.senderUserId, senderCardTypeStr).getOrNull()

                if (senderConn == null && senderPubKey != null) {
                    val safetyNumber = FortCryptoManager.computeSafetyNumber(
                        recipientCard.publicKey,
                        senderPubKey
                    )
                    val peerProfile = remoteBackend.fetchUserProfile(packet.senderUserId).getOrNull()
                    val peerName = peerProfile?.displayName ?: "Pass Peer"
                    val parsedSenderCardType = try { CardType.valueOf(senderCardTypeStr) } catch (_: Exception) { CardType.PERSONAL }
                    val autoConnection = PeerConnectionEntity(
                        connectionId = "conn_${UUID.randomUUID()}",
                        userId = currentUserId,
                        peerUserId = packet.senderUserId,
                        peerDisplayName = peerName,
                        peerHandle = peerProfile?.fortId ?: "@peer.${packet.senderUserId.takeLast(6)}",
                        peerCardType = parsedSenderCardType,
                        peerPublicKey = senderPubKey,
                        safetyNumber = safetyNumber,
                        isVerified = false,
                        passType = PassDurationType.SEVEN_DAYS,
                        passExpiresAt = Long.MAX_VALUE,
                        status = "ACTIVE"
                    )
                    database.peerConnectionDao().insertConnection(autoConnection)
                }

                val decrypted = FortCryptoManager.decrypt(
                    payload = com.fort.messenger.security.EncryptedMessagePayload(
                        ciphertextBase64 = packet.ciphertextBase64,
                        ivBase64 = packet.ivBase64,
                        ephemeralPublicKeyBase64 = packet.ephemeralKeyBase64,
                        senderFingerprint = "",
                        senderSignatureBase64 = packet.senderSignatureBase64
                    ),
                    recipientPrivateKeyBase64 = decryptedPriv,
                    senderPublicKeyBase64 = senderPubKey
                )

                val conversationId = "conv_${packet.senderUserId}"
                val encLocal = keyStoreMaster.encryptLocalData(decrypted)

                val localEntity = ChatMessageEntity(
                    messageId = packet.packetId,
                    conversationId = conversationId,
                    senderUserId = packet.senderUserId,
                    recipientUserId = currentUserId,
                    ciphertext = packet.ciphertextBase64,
                    iv = packet.ivBase64,
                    ephemeralKey = packet.ephemeralKeyBase64,
                    senderSignature = packet.senderSignatureBase64,
                    encryptedLocalPayload = encLocal,
                    decryptedTextCache = decrypted,
                    timestamp = packet.timestamp,
                    isMine = false,
                    isScrubbedMedia = decrypted.contains("[Metadata Sanitized", ignoreCase = true),
                    deliveryStatus = "DELIVERED"
                )
                database.chatMessageDao().insertMessage(localEntity)
                remoteBackend.updateDeliveryStatus(packet.packetId, "DELIVERED", currentUserId)
                decryptedCount++
            } catch (_: Exception) {
                // Ciphertext tampering or invalid signature fails closed
            }
        }
        return decryptedCount
    }

    suspend fun syncInboundMessages(currentUserId: String): Result<Int> {
        val packetsResult = remoteBackend.fetchPacketsForUser(currentUserId)
        if (packetsResult.isFailure) return Result.failure(packetsResult.exceptionOrNull()!!)
        val packets = packetsResult.getOrThrow()
        val count = processInboundPackets(packets, currentUserId)
        return Result.success(count)
    }

    suspend fun retryPendingOutbox(): Int {
        val pending = database.chatMessageDao().getPendingOutboxMessages()
        var retriedCount = 0
        for (msg in pending) {
            val packet = RemoteEncryptedPacket(
                packetId = msg.messageId,
                senderUserId = msg.senderUserId,
                recipientUserId = msg.recipientUserId,
                ciphertextBase64 = msg.ciphertext,
                ivBase64 = msg.iv,
                ephemeralKeyBase64 = msg.ephemeralKey,
                senderSignatureBase64 = msg.senderSignature,
                timestamp = msg.timestamp
            )
            val res = remoteBackend.sendEncryptedPacket(packet)
            if (res.isSuccess) {
                database.chatMessageDao().updateDeliveryStatus(msg.messageId, "SENT")
                retriedCount++
            }
        }
        return retriedCount
    }

    suspend fun addMessageReaction(messageId: String, currentUserId: String, emoji: String): Result<Unit> {
        val msg = database.chatMessageDao().getMessageById(messageId) ?: return Result.failure(IllegalArgumentException("Message not found."))
        val json = try { JSONObject(msg.reactionsJson) } catch (e: Exception) { JSONObject() }
        val usersArr = if (json.has(emoji)) json.getJSONArray(emoji) else JSONArray()
        var found = false
        val newArr = JSONArray()
        for (i in 0 until usersArr.length()) {
            val uid = usersArr.getString(i)
            if (uid == currentUserId) {
                found = true // Toggle off if already reacted with this emoji
            } else {
                newArr.put(uid)
            }
        }
        if (!found) {
            newArr.put(currentUserId)
        }
        if (newArr.length() > 0) {
            json.put(emoji, newArr)
        } else {
            json.remove(emoji)
        }
        database.chatMessageDao().updateMessageReactions(messageId, json.toString())
        return Result.success(Unit)
    }

    suspend fun editMessage(messageId: String, newText: String): Result<Unit> {
        val enc = keyStoreMaster.encryptLocalData(newText)
        database.chatMessageDao().editMessageContent(messageId, enc)
        return Result.success(Unit)
    }

    suspend fun deleteMessage(messageId: String): Result<Unit> {
        database.chatMessageDao().markMessageDeleted(messageId)
        return Result.success(Unit)
    }

    suspend fun markMessageRead(messageId: String): Result<Unit> {
        database.chatMessageDao().updateDeliveryStatus(messageId, "READ")
        return Result.success(Unit)
    }

    suspend fun getUnreadCount(conversationId: String): Int =
        database.chatMessageDao().getUnreadCount(conversationId)

    // --- Mood Ring & Quiet Presence ---

    fun getActiveMood(userId: String): Flow<MoodRingEntity?> =
        database.moodRingDao().getActiveMood(userId)

    suspend fun publishMood(
        userId: String,
        emotion: String,
        whatINeed: String?,
        audienceType: String,
        allowedAudienceIds: List<String>,
        decayDurationMinutes: Long
    ): Result<MoodRingEntity> {
        val now = System.currentTimeMillis()
        val expiresAt = if (decayDurationMinutes == -1L) {
            val localMidnight = LocalDate.now(ZoneId.systemDefault())
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            localMidnight
        } else {
            now + decayDurationMinutes * 60 * 1000L
        }

        val jsonAudience = JSONArray(allowedAudienceIds).toString()
        val localMood = MoodRingEntity(
            userId = userId,
            emotion = emotion,
            whatINeed = whatINeed,
            audienceType = audienceType,
            allowedAudienceIdsJson = jsonAudience,
            createdAt = now,
            expiresAt = expiresAt
        )
        database.moodRingDao().insertMood(localMood)

        val remoteRecord = RemoteMoodRecord(
            userId = userId,
            emotion = emotion,
            whatINeed = whatINeed,
            audienceType = audienceType,
            allowedAudienceIds = allowedAudienceIds,
            expiresAt = expiresAt
        )
        remoteBackend.publishMood(remoteRecord)
        return Result.success(localMood)
    }

    suspend fun clearMood(userId: String) {
        database.moodRingDao().clearMood(userId)
    }

    suspend fun fetchPeerMood(peerUserId: String, requesterUserId: String): Result<RemoteMoodRecord?> {
        return remoteBackend.fetchMood(peerUserId, requesterUserId)
    }

    // --- Private Rooms ---

    fun getActiveRooms(): Flow<List<PrivateRoomEntity>> = database.privateRoomDao().getActiveRooms()

    suspend fun createRoom(
        name: String,
        purpose: String,
        iconEmoji: String,
        creatorId: String,
        durationDays: Long,
        initialTasks: List<Pair<String, String>>
    ): Result<PrivateRoomEntity> {
        val now = System.currentTimeMillis()
        val expiresAt = now + durationDays * 86400000L
        val roomId = "room_${UUID.randomUUID()}"

        val membersArray = JSONArray().apply { put(creatorId) }
        val adminsArray = JSONArray().apply { put(creatorId) }

        val tasksArray = JSONArray()
        initialTasks.forEachIndexed { index, (title, assignedTo) ->
            tasksArray.put(JSONObject().apply {
                put("id", "t_$index")
                put("title", title)
                put("isCompleted", false)
                put("assignedTo", assignedTo)
            })
        }

        val remoteRecord = RemoteRoomRecord(
            roomId = roomId,
            name = name,
            creatorId = creatorId,
            members = mutableListOf(creatorId),
            adminIds = mutableListOf(creatorId),
            tasksJson = tasksArray.toString(),
            expiresAt = expiresAt
        )
        remoteBackend.createRoom(remoteRecord)

        val localEntity = PrivateRoomEntity(
            roomId = roomId,
            name = name,
            purpose = purpose,
            iconEmoji = iconEmoji,
            creatorId = creatorId,
            membersJson = membersArray.toString(),
            adminIdsJson = adminsArray.toString(),
            tasksJson = tasksArray.toString(),
            createdAt = now,
            expiresAt = expiresAt,
            isClosed = false
        )
        database.privateRoomDao().insertRoom(localEntity)
        return Result.success(localEntity)
    }

    suspend fun inviteToRoom(roomId: String, requesterUserId: String, inviteeUserId: String): Result<Unit> {
        val remoteRes = remoteBackend.inviteToRoom(roomId, requesterUserId, inviteeUserId)
        if (remoteRes.isFailure) return remoteRes
        val room = database.privateRoomDao().getRoomById(roomId) ?: return Result.failure(IllegalArgumentException("Room not found."))
        val membersArr = JSONArray(room.membersJson)
        membersArr.put(inviteeUserId)
        database.privateRoomDao().updateRoomMembers(roomId, membersArr.toString(), room.adminIdsJson)
        return Result.success(Unit)
    }

    suspend fun removeRoomMember(roomId: String, requesterUserId: String, memberToRemoveId: String): Result<Unit> {
        val remoteRes = remoteBackend.removeRoomMember(roomId, requesterUserId, memberToRemoveId)
        if (remoteRes.isFailure) return remoteRes
        val room = database.privateRoomDao().getRoomById(roomId) ?: return Result.failure(IllegalArgumentException("Room not found."))
        val membersArr = JSONArray(room.membersJson)
        val newArr = JSONArray()
        for (i in 0 until membersArr.length()) {
            if (membersArr.getString(i) != memberToRemoveId) newArr.put(membersArr.getString(i))
        }
        database.privateRoomDao().updateRoomMembers(roomId, newArr.toString(), room.adminIdsJson)
        return Result.success(Unit)
    }

    suspend fun leaveRoom(roomId: String, userId: String): Result<Unit> {
        val remoteRes = remoteBackend.leaveRoom(roomId, userId)
        if (remoteRes.isFailure) return remoteRes
        val room = database.privateRoomDao().getRoomById(roomId) ?: return Result.failure(IllegalArgumentException("Room not found."))
        val membersArr = JSONArray(room.membersJson)
        val newArr = JSONArray()
        for (i in 0 until membersArr.length()) {
            if (membersArr.getString(i) != userId) newArr.put(membersArr.getString(i))
        }
        database.privateRoomDao().updateRoomMembers(roomId, newArr.toString(), room.adminIdsJson)
        return Result.success(Unit)
    }

    suspend fun toggleRoomTask(roomId: String, taskId: String): Result<Unit> {
        val room = database.privateRoomDao().getRoomById(roomId) ?: return Result.failure(IllegalArgumentException("Room not found."))
        val tasksArray = JSONArray(room.tasksJson)
        for (i in 0 until tasksArray.length()) {
            val taskObj = tasksArray.getJSONObject(i)
            if (taskObj.getString("id") == taskId) {
                taskObj.put("isCompleted", !taskObj.getBoolean("isCompleted"))
            }
        }
        val updatedJson = tasksArray.toString()
        database.privateRoomDao().updateRoomTasks(roomId, updatedJson)
        return Result.success(Unit)
    }

    // --- User Discovery & Search ---

    suspend fun searchUsers(
        query: String,
        mode: SearchMode,
        requesterUserId: String
    ): Result<List<UserSearchResult>> {
        val remoteResult = remoteBackend.searchUsers(query, mode, requesterUserId)
        if (remoteResult.isFailure) return remoteResult
        val list = remoteResult.getOrThrow()

        val activeConnections = database.peerConnectionDao().getActiveConnections(requesterUserId).firstOrNull() ?: emptyList()
        val connectedPeerIds = activeConnections.map { it.peerUserId }.toSet()

        val mapped = list.map {
            it.copy(isExistingConnection = connectedPeerIds.contains(it.userId))
        }
        return Result.success(mapped)
    }

    suspend fun updateUserDiscoveryPrivacy(
        userId: String,
        discoverableByName: Boolean,
        discoverableByPhone: Boolean
    ): Result<Unit> {
        return remoteBackend.updateUserDiscoveryPrivacy(userId, discoverableByName, discoverableByPhone)
    }

    // --- WebRTC Audio & Video Call Signaling ---

    suspend fun createCall(call: RemoteCallRecord): Result<Unit> {
        return remoteBackend.createCall(call)
    }

    suspend fun updateCallStatus(callId: String, status: String, requesterUserId: String): Result<Unit> {
        return remoteBackend.updateCallStatus(callId, status, requesterUserId)
    }

    suspend fun setCallOffer(callId: String, sdp: String, requesterUserId: String): Result<Unit> {
        return remoteBackend.setCallOffer(callId, sdp, requesterUserId)
    }

    suspend fun setCallAnswer(callId: String, sdp: String, requesterUserId: String): Result<Unit> {
        return remoteBackend.setCallAnswer(callId, sdp, requesterUserId)
    }

    suspend fun sendCallIceCandidate(
        callId: String,
        candidate: RtcIceCandidateRecord,
        isCaller: Boolean,
        requesterUserId: String
    ): Result<Unit> {
        return remoteBackend.sendCallIceCandidate(callId, candidate, isCaller, requesterUserId)
    }

    fun listenToCall(callId: String): Flow<RemoteCallRecord?> {
        return remoteBackend.listenToCall(callId)
    }

    fun listenToIncomingCalls(userId: String): Flow<RemoteCallRecord?> {
        return remoteBackend.listenToIncomingCalls(userId)
    }

    fun listenToCallCandidates(callId: String, isCaller: Boolean): Flow<List<RtcIceCandidateRecord>> {
        return remoteBackend.listenToCallCandidates(callId, isCaller)
    }

    // --- Ephemeral Private Location Sharing ---

    suspend fun sendLocationPin(
        conversationId: String,
        senderUserId: String,
        recipientUserId: String,
        pin: LocationPin
    ): Result<ChatMessageEntity> {
        val labelStr = pin.label?.let { " - $it" } ?: ""
        val text = "📍 Shared Location Pin: ${pin.latitude}, ${pin.longitude}$labelStr"
        return sendEncryptedMessage(
            conversationId = conversationId,
            senderUserId = senderUserId,
            recipientUserId = recipientUserId,
            plaintext = text
        )
    }

    suspend fun startLiveLocationSharing(
        senderUserId: String,
        senderDisplayName: String,
        recipientUserId: String,
        duration: LiveLocationDuration,
        initialLat: Double,
        initialLng: Double,
        accuracy: Float = 0f
    ): Result<LiveLocationSession> {
        val now = System.currentTimeMillis()
        val session = LiveLocationSession(
            shareId = "loc_${UUID.randomUUID()}",
            senderUserId = senderUserId,
            senderDisplayName = senderDisplayName,
            recipientUserId = recipientUserId,
            latitude = initialLat,
            longitude = initialLng,
            accuracyMeters = accuracy,
            startedAt = now,
            expiresAt = now + duration.durationMillis,
            isStopped = false,
            lastUpdated = now
        )
        val publishResult = remoteBackend.publishLiveLocation(session)
        if (publishResult.isFailure) return Result.failure(publishResult.exceptionOrNull()!!)

        // Send encrypted notification message
        sendEncryptedMessage(
            conversationId = "conv_${recipientUserId}",
            senderUserId = senderUserId,
            recipientUserId = recipientUserId,
            plaintext = "🛰️ Started sharing live location (${duration.label})"
        )

        return Result.success(session)
    }

    suspend fun updateLiveLocation(
        shareId: String,
        senderUserId: String,
        senderDisplayName: String,
        recipientUserId: String,
        lat: Double,
        lng: Double,
        accuracy: Float,
        startedAt: Long,
        expiresAt: Long
    ): Result<Unit> {
        val session = LiveLocationSession(
            shareId = shareId,
            senderUserId = senderUserId,
            senderDisplayName = senderDisplayName,
            recipientUserId = recipientUserId,
            latitude = lat,
            longitude = lng,
            accuracyMeters = accuracy,
            startedAt = startedAt,
            expiresAt = expiresAt,
            isStopped = false,
            lastUpdated = System.currentTimeMillis()
        )
        return remoteBackend.publishLiveLocation(session)
    }

    suspend fun stopLiveLocationSharing(
        shareId: String,
        requesterUserId: String,
        recipientUserId: String
    ): Result<Unit> {
        val res = remoteBackend.stopLiveLocation(shareId, requesterUserId)
        sendEncryptedMessage(
            conversationId = "conv_${recipientUserId}",
            senderUserId = requesterUserId,
            recipientUserId = recipientUserId,
            plaintext = "🛑 Stopped sharing live location"
        )
        return res
    }

    suspend fun fetchActiveLiveLocation(senderUserId: String, recipientUserId: String): Result<LiveLocationSession?> {
        return remoteBackend.fetchActiveLiveLocation(senderUserId, recipientUserId)
    }
}
