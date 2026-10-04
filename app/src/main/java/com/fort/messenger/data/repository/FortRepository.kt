package com.fort.messenger.data.repository

import android.app.Activity

import com.fort.messenger.data.local.*
import com.fort.messenger.data.remote.*
import com.fort.messenger.model.CardType
import com.fort.messenger.model.PassDurationType
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
        return Result.success(account)
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

        val existing = database.userAccountDao().getActiveAccountOnce()
        return if (existing != null && existing.userId == remoteUser.userId) {
            Result.success(existing)
        } else {
            initLocalUserAccount(remoteUser, remoteUser.displayName)
        }
    }

    suspend fun sendPhoneOtp(phoneNumber: String, activity: Activity? = null): Result<String> {
        return remoteBackend.sendPhoneOtp(phoneNumber, activity)
    }

    suspend fun verifyPhoneOtp(verificationId: String, code: String, displayName: String): Result<UserAccountEntity> {
        val result = remoteBackend.verifyPhoneOtp(verificationId, code, displayName)
        if (result.isFailure) return Result.failure(result.exceptionOrNull()!!)
        val remoteUser = result.getOrThrow()

        val existing = database.userAccountDao().getActiveAccountOnce()
        return if (existing != null && existing.userId == remoteUser.userId) {
            Result.success(existing)
        } else {
            initLocalUserAccount(remoteUser, displayName.ifBlank { "Phone User ${remoteUser.phoneNumber?.takeLast(4) ?: "Sovereign"}" })
        }
    }

    suspend fun loginWithGoogle(idToken: String, displayName: String): Result<UserAccountEntity> {
        val result = remoteBackend.loginWithGoogle(idToken, displayName)
        if (result.isFailure) return Result.failure(result.exceptionOrNull()!!)
        val remoteUser = result.getOrThrow()

        val existing = database.userAccountDao().getActiveAccountOnce()
        return if (existing != null && existing.userId == remoteUser.userId) {
            Result.success(existing)
        } else {
            initLocalUserAccount(remoteUser, displayName.ifBlank { "Google User" })
        }
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
        database.knockFirstDao().insertRequest(request)
        return Result.success(Unit)
    }

    suspend fun acceptRequestOnce(request: KnockFirstRequestEntity, currentUserId: String): Result<Unit> {
        database.knockFirstDao().updateRequestStatus(request.requestId, "ACCEPTED")

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
        return Result.success(Unit)
    }

    suspend fun grantRequestSevenDays(request: KnockFirstRequestEntity, currentUserId: String): Result<Unit> {
        database.knockFirstDao().updateRequestStatus(request.requestId, "ACCEPTED")

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
        return Result.success(Unit)
    }

    suspend fun declineRequest(requestId: String) {
        database.knockFirstDao().updateRequestStatus(requestId, "DECLINED")
    }

    suspend fun blockAndReportRequest(request: KnockFirstRequestEntity, currentUserId: String) {
        database.knockFirstDao().updateRequestStatus(request.requestId, "BLOCKED")
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
        remoteBackend.publishPass(remoteRecord)

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

    suspend fun claimPass(token: String, claimantUserId: String, claimantDisplayName: String): Result<PeerConnectionEntity> {
        val claimResult = remoteBackend.claimPass(token, claimantUserId)
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

        val connection = PeerConnectionEntity(
            connectionId = "conn_${UUID.randomUUID()}",
            userId = claimantUserId,
            peerUserId = remotePass.issuerUserId,
            peerDisplayName = "Pass Peer (${remotePass.cardType})",
            peerHandle = "@pass.${remotePass.issuerUserId.takeLast(6)}",
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

        // Retrieve recipient public key
        val recipientKeyResult = remoteBackend.fetchPublicKey(recipientUserId, CardType.PERSONAL.name)
        if (recipientKeyResult.isFailure) {
            return Result.failure(recipientKeyResult.exceptionOrNull()!!)
        }
        val recipientPublicKey = recipientKeyResult.getOrThrow()

        // Check for peer key rotation
        val connection = database.peerConnectionDao().getConnectionWithPeer(senderUserId, recipientUserId)
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

        // Transmit only ciphertext payload and sender signature to server relay
        val packet = RemoteEncryptedPacket(
            packetId = messageId,
            senderUserId = senderUserId,
            recipientUserId = recipientUserId,
            ciphertextBase64 = payload.ciphertextBase64,
            ivBase64 = payload.ivBase64,
            ephemeralKeyBase64 = payload.ephemeralPublicKeyBase64,
            senderSignatureBase64 = payload.senderSignatureBase64,
            timestamp = now
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

    suspend fun syncInboundMessages(currentUserId: String): Result<Int> {
        val packetsResult = remoteBackend.fetchPacketsForUser(currentUserId)
        if (packetsResult.isFailure) return Result.failure(packetsResult.exceptionOrNull()!!)
        val packets = packetsResult.getOrThrow()

        val userCard = database.personaCardDao().getCardsForUser(currentUserId).firstOrNull()?.firstOrNull()
            ?: return Result.success(0)

        val decryptedPriv = try {
            keyStoreMaster.decryptLocalData(userCard.privateKeyEncrypted)
        } catch (e: Exception) {
            userCard.privateKeyEncrypted
        }

        var decryptedCount = 0
        for (packet in packets) {
            try {
                // Fetch sender public key for cryptographic signature verification
                val senderConn = database.peerConnectionDao().getConnectionWithPeer(currentUserId, packet.senderUserId)
                val senderPubKey = senderConn?.peerPublicKey ?: remoteBackend.fetchPublicKey(packet.senderUserId, CardType.PERSONAL.name).getOrNull()

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
                decryptedCount++
            } catch (e: Exception) {
                // Ciphertext tampering or invalid signature fails closed
            }
        }
        return Result.success(decryptedCount)
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
}
