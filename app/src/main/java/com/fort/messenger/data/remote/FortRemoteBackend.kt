package com.fort.messenger.data.remote

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class RemoteUserAccount(
    val userId: String,
    val email: String,
    val passwordHash: String,
    val publicKeys: MutableMap<String, String> = ConcurrentHashMap() // cardType -> publicKeyBase64
)

data class RemoteEncryptedPacket(
    val packetId: String,
    val senderUserId: String,
    val recipientUserId: String,
    val ciphertextBase64: String,
    val ivBase64: String,
    val ephemeralKeyBase64: String,
    val timestamp: Long
)

data class RemotePassRecord(
    val passId: String,
    val issuerUserId: String,
    val token: String,
    val cardType: String,
    val durationType: String,
    val expiresAt: Long,
    val isSingleUse: Boolean,
    var isClaimed: Boolean = false,
    var isRevoked: Boolean = false,
    val issuerPublicKey: String
)

data class RemoteRoomRecord(
    val roomId: String,
    val name: String,
    val creatorId: String,
    val members: MutableList<String> = CopyOnWriteArrayList(),
    var tasksJson: String,
    val expiresAt: Long
)

data class RemoteMoodRecord(
    val userId: String,
    val emotion: String,
    val whatINeed: String?,
    val audienceType: String,
    val allowedAudienceIds: List<String>,
    val expiresAt: Long
)

interface FortRemoteBackend {
    suspend fun register(email: String, password: String): Result<RemoteUserAccount>
    suspend fun login(email: String, password: String): Result<RemoteUserAccount>
    suspend fun publishPublicKey(userId: String, cardType: String, publicKey: String): Result<Unit>
    suspend fun fetchPublicKey(userId: String, cardType: String): Result<String>
    suspend fun publishPass(pass: RemotePassRecord): Result<Unit>
    suspend fun claimPass(token: String, claimantUserId: String): Result<RemotePassRecord>
    suspend fun revokePass(passId: String, requesterUserId: String): Result<Unit>
    suspend fun sendEncryptedPacket(packet: RemoteEncryptedPacket): Result<Unit>
    suspend fun fetchPacketsForUser(recipientUserId: String): Result<List<RemoteEncryptedPacket>>
    suspend fun publishMood(mood: RemoteMoodRecord): Result<Unit>
    suspend fun fetchMood(targetUserId: String, requesterUserId: String): Result<RemoteMoodRecord?>
    suspend fun createRoom(room: RemoteRoomRecord): Result<Unit>
    suspend fun fetchRoom(roomId: String, requesterUserId: String): Result<RemoteRoomRecord>
    suspend fun blockUser(blockerUserId: String, blockedUserId: String): Result<Unit>
    suspend fun isBlocked(potentialSenderId: String, targetRecipientId: String): Boolean
}

/**
 * Server Relay implementing authentic server-side authorization checks.
 * Enforces access controls, blocklists, pass validity, and audience isolation.
 */
class InMemoryRemoteRelay : FortRemoteBackend {

    private val users = ConcurrentHashMap<String, RemoteUserAccount>() // email -> user
    private val usersById = ConcurrentHashMap<String, RemoteUserAccount>() // userId -> user
    private val passesByToken = ConcurrentHashMap<String, RemotePassRecord>()
    private val passesById = ConcurrentHashMap<String, RemotePassRecord>()
    private val packets = CopyOnWriteArrayList<RemoteEncryptedPacket>()
    private val moods = ConcurrentHashMap<String, RemoteMoodRecord>() // userId -> mood
    private val rooms = ConcurrentHashMap<String, RemoteRoomRecord>() // roomId -> room
    private val blocklists = ConcurrentHashMap<String, MutableSet<String>>() // blockerUserId -> set of blockedUserIds

    override suspend fun register(email: String, password: String): Result<RemoteUserAccount> {
        val cleanEmail = email.trim().lowercase()
        if (users.containsKey(cleanEmail)) {
            return Result.failure(IllegalArgumentException("Account with this email already exists."))
        }
        val userId = "usr_${cleanEmail.hashCode().toUInt()}"
        val user = RemoteUserAccount(
            userId = userId,
            email = cleanEmail,
            passwordHash = "hash_${password.hashCode()}"
        )
        users[cleanEmail] = user
        usersById[userId] = user
        return Result.success(user)
    }

    override suspend fun login(email: String, password: String): Result<RemoteUserAccount> {
        val cleanEmail = email.trim().lowercase()
        val user = users[cleanEmail] ?: return Result.failure(IllegalArgumentException("No account found for $cleanEmail."))
        if (user.passwordHash != "hash_${password.hashCode()}") {
            return Result.failure(SecurityException("Invalid credentials provided."))
        }
        return Result.success(user)
    }

    override suspend fun publishPublicKey(userId: String, cardType: String, publicKey: String): Result<Unit> {
        val user = usersById[userId] ?: return Result.failure(IllegalArgumentException("User not found."))
        user.publicKeys[cardType] = publicKey
        return Result.success(Unit)
    }

    override suspend fun fetchPublicKey(userId: String, cardType: String): Result<String> {
        val user = usersById[userId] ?: return Result.failure(IllegalArgumentException("User not found."))
        val key = user.publicKeys[cardType] ?: user.publicKeys.values.firstOrNull()
        return if (key != null) Result.success(key) else Result.failure(IllegalStateException("No public key registered for user."))
    }

    override suspend fun publishPass(pass: RemotePassRecord): Result<Unit> {
        passesByToken[pass.token] = pass
        passesById[pass.passId] = pass
        return Result.success(Unit)
    }

    override suspend fun claimPass(token: String, claimantUserId: String): Result<RemotePassRecord> {
        val pass = passesByToken[token] ?: return Result.failure(IllegalArgumentException("Pass token not found or invalid."))

        val now = System.currentTimeMillis()
        if (pass.isRevoked) {
            return Result.failure(SecurityException("Server Authorization: Pass was explicitly revoked by issuer."))
        }
        if (now > pass.expiresAt) {
            return Result.failure(SecurityException("Server Authorization: Pass validity window has expired."))
        }
        if (pass.isSingleUse && pass.isClaimed) {
            return Result.failure(SecurityException("Server Authorization: Single-use pass has already been claimed."))
        }
        if (isBlocked(claimantUserId, pass.issuerUserId)) {
            return Result.failure(SecurityException("Server Authorization: Claimant is blocked by pass issuer."))
        }

        pass.isClaimed = true
        return Result.success(pass)
    }

    override suspend fun revokePass(passId: String, requesterUserId: String): Result<Unit> {
        val pass = passesById[passId] ?: return Result.failure(IllegalArgumentException("Pass not found."))
        if (pass.issuerUserId != requesterUserId) {
            return Result.failure(SecurityException("Server Authorization: Only the pass issuer can revoke access."))
        }
        pass.isRevoked = true
        return Result.success(Unit)
    }

    override suspend fun sendEncryptedPacket(packet: RemoteEncryptedPacket): Result<Unit> {
        // Enforce Server-Side Blocklist: Drop packet if sender is blocked by recipient
        if (isBlocked(packet.senderUserId, packet.recipientUserId)) {
            return Result.failure(SecurityException("Server Authorization: Inbound transmission denied. Sender is blocked by recipient."))
        }
        packets.add(packet)
        return Result.success(Unit)
    }

    override suspend fun fetchPacketsForUser(recipientUserId: String): Result<List<RemoteEncryptedPacket>> {
        val forUser = packets.filter { it.recipientUserId == recipientUserId }
        return Result.success(forUser)
    }

    override suspend fun publishMood(mood: RemoteMoodRecord): Result<Unit> {
        moods[mood.userId] = mood
        return Result.success(Unit)
    }

    override suspend fun fetchMood(targetUserId: String, requesterUserId: String): Result<RemoteMoodRecord?> {
        val mood = moods[targetUserId] ?: return Result.success(null)

        // Check real temporal expiry
        if (System.currentTimeMillis() >= mood.expiresAt) {
            moods.remove(targetUserId)
            return Result.success(null)
        }

        // Enforce Server-Side Audience Isolation (PRD Section 4.3)
        return when (mood.audienceType) {
            "PRIVATE" -> {
                if (targetUserId == requesterUserId) Result.success(mood) else Result.success(null)
            }
            "CONNECTIONS" -> {
                if (isBlocked(requesterUserId, targetUserId)) Result.success(null) else Result.success(mood)
            }
            "SELECTED_PEOPLE", "CIRCLES" -> {
                if (targetUserId == requesterUserId || mood.allowedAudienceIds.contains(requesterUserId)) {
                    Result.success(mood)
                } else {
                    Result.success(null)
                }
            }
            else -> Result.success(null)
        }
    }

    override suspend fun createRoom(room: RemoteRoomRecord): Result<Unit> {
        rooms[room.roomId] = room
        return Result.success(Unit)
    }

    override suspend fun fetchRoom(roomId: String, requesterUserId: String): Result<RemoteRoomRecord> {
        val room = rooms[roomId] ?: return Result.failure(IllegalArgumentException("Room not found."))

        // Enforce Server-Side Room Membership Check (PRD Section 3.1 & 4.2)
        if (!room.members.contains(requesterUserId) && room.creatorId != requesterUserId) {
            return Result.failure(SecurityException("Server Authorization: Requester is not an enrolled member of this private room."))
        }
        return Result.success(room)
    }

    override suspend fun blockUser(blockerUserId: String, blockedUserId: String): Result<Unit> {
        val list = blocklists.getOrPut(blockerUserId) { ConcurrentHashMap.newKeySet() }
        list.add(blockedUserId)
        return Result.success(Unit)
    }

    override suspend fun isBlocked(potentialSenderId: String, targetRecipientId: String): Boolean {
        return blocklists[targetRecipientId]?.contains(potentialSenderId) == true
    }

    fun clearAllData() {
        users.clear()
        usersById.clear()
        passesByToken.clear()
        passesById.clear()
        packets.clear()
        moods.clear()
        rooms.clear()
        blocklists.clear()
    }
}
