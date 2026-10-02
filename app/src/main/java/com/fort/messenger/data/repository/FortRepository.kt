package com.fort.messenger.data.repository

import com.fort.messenger.data.local.*
import com.fort.messenger.data.remote.*
import com.fort.messenger.model.CardType
import com.fort.messenger.model.PassDurationType
import com.fort.messenger.security.FortCryptoManager
import com.fort.messenger.security.IdentityKeyPair
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class FortRepository(
    private val database: FortDatabase,
    private val remoteBackend: FortRemoteBackend
) {

    // --- Authentication & Session Management ---

    fun getActiveAccount(): Flow<UserAccountEntity?> = database.userAccountDao().getActiveAccount()

    suspend fun register(email: String, password: String, displayName: String): Result<UserAccountEntity> {
        val remoteResult = remoteBackend.register(email, password)
        if (remoteResult.isFailure) {
            return Result.failure(remoteResult.exceptionOrNull()!!)
        }
        val remoteUser = remoteResult.getOrThrow()

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

        val activeCardId = "card_${remoteUser.userId}_personal"
        val account = UserAccountEntity(
            userId = remoteUser.userId,
            email = remoteUser.email,
            authToken = "tok_${UUID.randomUUID()}",
            activeCardId = activeCardId,
            biometricEnabled = false,
            redactNotifications = true
        )
        database.userAccountDao().insertAccount(account)

        val cards = listOf(
            PersonaCardEntity(
                cardId = activeCardId,
                userId = remoteUser.userId,
                type = CardType.PERSONAL,
                displayName = displayName,
                handle = "@${displayName.lowercase().replace(" ", "")}.personal",
                bio = "Personal circle only • Close verified contacts",
                avatarEmoji = "🛡️",
                publicKey = personalKey.publicKeyBase64,
                privateKeyEncrypted = personalKey.privateKeyBase64,
                businessHoursOnly = false,
                moodSharingEnabled = true
            ),
            PersonaCardEntity(
                cardId = "card_${remoteUser.userId}_work",
                userId = remoteUser.userId,
                type = CardType.WORK,
                displayName = "$displayName, Professional",
                handle = "@${displayName.lowercase().replace(" ", "")}.work",
                bio = "Professional engagements • Business hours only",
                avatarEmoji = "💼",
                publicKey = workKey.publicKeyBase64,
                privateKeyEncrypted = workKey.privateKeyBase64,
                businessHoursOnly = true,
                moodSharingEnabled = false
            ),
            PersonaCardEntity(
                cardId = "card_${remoteUser.userId}_travel",
                userId = remoteUser.userId,
                type = CardType.TRAVEL,
                displayName = "$displayName (Transit)",
                handle = "@${displayName.lowercase().replace(" ", "")}.travel",
                bio = "Expedition & transit beacons • Ephemeral passes",
                avatarEmoji = "🧭",
                publicKey = travelKey.publicKeyBase64,
                privateKeyEncrypted = travelKey.privateKeyBase64,
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
                privateKeyEncrypted = marketKey.privateKeyBase64,
                businessHoursOnly = false,
                moodSharingEnabled = false
            )
        )
        database.personaCardDao().insertCards(cards)
        return Result.success(account)
    }

    suspend fun login(email: String, password: String): Result<UserAccountEntity> {
        val remoteResult = remoteBackend.login(email, password)
        if (remoteResult.isFailure) return Result.failure(remoteResult.exceptionOrNull()!!)
        val remoteUser = remoteResult.getOrThrow()

        // Check if account already exists locally, else initialize local state
        val existing = database.userAccountDao().getActiveAccountOnce()
        return if (existing != null && existing.userId == remoteUser.userId) {
            Result.success(existing)
        } else {
            register(email, password, email.substringBefore("@").replaceFirstChar { it.uppercase() })
        }
    }

    suspend fun logout() {
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

        // Fetch peer public key from server
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
            passExpiresAt = System.currentTimeMillis() + 86400000L, // 24 hours idle limit
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

    suspend fun sendEncryptedMessage(
        conversationId: String,
        senderUserId: String,
        recipientUserId: String,
        plaintext: String,
        isScrubbedMedia: Boolean = false
    ): Result<ChatMessageEntity> {
        val senderAccount = database.userAccountDao().getActiveAccountOnce()
            ?: return Result.failure(IllegalStateException("No active account."))
        val senderCard = database.personaCardDao().getCardById(senderAccount.activeCardId)
            ?: return Result.failure(IllegalStateException("No active persona card."))

        val senderKeyPair = IdentityKeyPair(
            publicKeyBase64 = senderCard.publicKey,
            privateKeyBase64 = senderCard.privateKeyEncrypted,
            fingerprint = FortCryptoManager.computeFingerprint(android.util.Base64.decode(senderCard.publicKey, android.util.Base64.NO_WRAP))
        )

        // Retrieve recipient public key
        val recipientKeyResult = remoteBackend.fetchPublicKey(recipientUserId, CardType.PERSONAL.name)
        if (recipientKeyResult.isFailure) {
            return Result.failure(recipientKeyResult.exceptionOrNull()!!)
        }
        val recipientPublicKey = recipientKeyResult.getOrThrow()

        // Check if recipient rotated keys since last interaction
        val connection = database.peerConnectionDao().getConnectionWithPeer(senderUserId, recipientUserId)
        if (connection != null && connection.peerPublicKey.isNotEmpty() && connection.peerPublicKey != recipientPublicKey) {
            database.peerConnectionDao().reportKeyRotation(connection.connectionId, recipientPublicKey)
        }

        // Encrypt with authentic ECDH + HKDF + AES-256-GCM
        val payload = FortCryptoManager.encrypt(
            plaintext = plaintext,
            recipientPublicKeyBase64 = recipientPublicKey,
            senderKeyPair = senderKeyPair
        )

        val messageId = "msg_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()

        // Transmit only ciphertext payload to server relay
        val packet = RemoteEncryptedPacket(
            packetId = messageId,
            senderUserId = senderUserId,
            recipientUserId = recipientUserId,
            ciphertextBase64 = payload.ciphertextBase64,
            ivBase64 = payload.ivBase64,
            ephemeralKeyBase64 = payload.ephemeralPublicKeyBase64,
            timestamp = now
        )
        val transmitResult = remoteBackend.sendEncryptedPacket(packet)
        if (transmitResult.isFailure) {
            return Result.failure(transmitResult.exceptionOrNull()!!)
        }

        val localEntity = ChatMessageEntity(
            messageId = messageId,
            conversationId = conversationId,
            senderUserId = senderUserId,
            recipientUserId = recipientUserId,
            ciphertext = payload.ciphertextBase64,
            iv = payload.ivBase64,
            ephemeralKey = payload.ephemeralPublicKeyBase64,
            decryptedTextCache = plaintext,
            timestamp = now,
            isMine = true,
            isScrubbedMedia = isScrubbedMedia,
            deliveryStatus = "SENT"
        )
        database.chatMessageDao().insertMessage(localEntity)
        return Result.success(localEntity)
    }

    suspend fun syncInboundMessages(currentUserId: String): Result<Int> {
        val packetsResult = remoteBackend.fetchPacketsForUser(currentUserId)
        if (packetsResult.isFailure) return Result.failure(packetsResult.exceptionOrNull()!!)
        val packets = packetsResult.getOrThrow()

        val userCard = database.personaCardDao().getCardsForUser(currentUserId).firstOrNull()?.firstOrNull()
            ?: return Result.success(0)

        var decryptedCount = 0
        for (packet in packets) {
            try {
                val decrypted = FortCryptoManager.decrypt(
                    payload = com.fort.messenger.security.EncryptedMessagePayload(
                        ciphertextBase64 = packet.ciphertextBase64,
                        ivBase64 = packet.ivBase64,
                        ephemeralPublicKeyBase64 = packet.ephemeralKeyBase64,
                        senderFingerprint = ""
                    ),
                    recipientPrivateKeyBase64 = userCard.privateKeyEncrypted
                )

                val conversationId = "conv_${packet.senderUserId}"
                val localEntity = ChatMessageEntity(
                    messageId = packet.packetId,
                    conversationId = conversationId,
                    senderUserId = packet.senderUserId,
                    recipientUserId = currentUserId,
                    ciphertext = packet.ciphertextBase64,
                    iv = packet.ivBase64,
                    ephemeralKey = packet.ephemeralKeyBase64,
                    decryptedTextCache = decrypted,
                    timestamp = packet.timestamp,
                    isMine = false,
                    isScrubbedMedia = decrypted.contains("[Metadata Sanitized", ignoreCase = true),
                    deliveryStatus = "DELIVERED"
                )
                database.chatMessageDao().insertMessage(localEntity)
                decryptedCount++
            } catch (e: Exception) {
                // Ciphertext tampering or key mismatch
            }
        }
        return Result.success(decryptedCount)
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
            // End of user's local day
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

        val membersArray = JSONArray().apply {
            put(creatorId)
        }

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
            tasksJson = tasksArray.toString(),
            createdAt = now,
            expiresAt = expiresAt,
            isClosed = false
        )
        database.privateRoomDao().insertRoom(localEntity)
        return Result.success(localEntity)
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
