package com.fort.messenger.data.remote

import android.util.Base64
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class RemoteUserAccount(
    val userId: String,
    val email: String,
    val passwordHash: String,
    val phoneNumber: String? = null,
    val displayName: String = "",
    val publicKeys: MutableMap<String, String> = ConcurrentHashMap() // cardType -> publicKeyBase64
)

data class RemoteEncryptedPacket(
    val packetId: String,
    val senderUserId: String,
    val recipientUserId: String,
    val ciphertextBase64: String,
    val ivBase64: String,
    val ephemeralKeyBase64: String,
    val senderSignatureBase64: String = "",
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
    var claimantUserId: String? = null,
    val issuerPublicKey: String
)

data class RemoteRoomRecord(
    val roomId: String,
    val name: String,
    val creatorId: String,
    val members: MutableList<String> = CopyOnWriteArrayList(),
    val adminIds: MutableList<String> = CopyOnWriteArrayList(),
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
    suspend fun register(email: String, password: String, displayName: String = ""): Result<RemoteUserAccount>
    suspend fun login(email: String, password: String): Result<RemoteUserAccount>
    suspend fun sendPhoneOtp(phoneNumber: String): Result<String>
    suspend fun verifyPhoneOtp(verificationId: String, code: String, displayName: String = ""): Result<RemoteUserAccount>
    suspend fun loginWithGoogle(idToken: String, displayName: String = ""): Result<RemoteUserAccount>
    suspend fun sendPasswordReset(email: String): Result<Unit>
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
    suspend fun inviteToRoom(roomId: String, requesterUserId: String, inviteeUserId: String): Result<Unit>
    suspend fun removeRoomMember(roomId: String, requesterUserId: String, memberToRemoveId: String): Result<Unit>
    suspend fun leaveRoom(roomId: String, userId: String): Result<Unit>
    suspend fun blockUser(blockerUserId: String, blockedUserId: String): Result<Unit>
    suspend fun isBlocked(potentialSenderId: String, targetRecipientId: String): Boolean
}

/**
 * Server Relay implementing authentic server-side authorization checks.
 * Enforces access controls, blocklists, pass validity, and audience isolation.
 * For use in testing and automated verification.
 */
class InMemoryRemoteRelay : FortRemoteBackend {

    private val users = ConcurrentHashMap<String, RemoteUserAccount>() // email -> user
    private val usersById = ConcurrentHashMap<String, RemoteUserAccount>() // userId -> user
    private val usersByPhone = ConcurrentHashMap<String, RemoteUserAccount>()
    private val passesByToken = ConcurrentHashMap<String, RemotePassRecord>()
    private val passesById = ConcurrentHashMap<String, RemotePassRecord>()
    private val packets = CopyOnWriteArrayList<RemoteEncryptedPacket>()
    private val moods = ConcurrentHashMap<String, RemoteMoodRecord>() // userId -> mood
    private val rooms = ConcurrentHashMap<String, RemoteRoomRecord>() // roomId -> room
    private val blocklists = ConcurrentHashMap<String, MutableSet<String>>() // blockerUserId -> set of blockedUserIds
    private val phoneOtpSessions = ConcurrentHashMap<String, Pair<String, String>>() // verificationId -> (phoneNumber, code)

    private val secureRandom = SecureRandom()

    // PBKDF2 Password Hashing (Never String.hashCode())
    private fun hashPassword(password: String, salt: ByteArray): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, 10000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(salt, Base64.NO_WRAP) + ":" + Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    private fun verifyPassword(password: String, storedHash: String): Boolean {
        val parts = storedHash.split(":")
        if (parts.size != 2) return false
        val salt = Base64.decode(parts[0], Base64.NO_WRAP)
        val expectedHash = parts[1]
        val spec = PBEKeySpec(password.toCharArray(), salt, 10000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val computedHash = Base64.encodeToString(factory.generateSecret(spec).encoded, Base64.NO_WRAP)
        return computedHash == expectedHash
    }

    override suspend fun register(email: String, password: String, displayName: String): Result<RemoteUserAccount> {
        val cleanEmail = email.trim().lowercase()
        if (users.containsKey(cleanEmail)) {
            return Result.failure(IllegalArgumentException("Account with this email already exists."))
        }
        val salt = ByteArray(16).apply { secureRandom.nextBytes(this) }
        val passwordHash = hashPassword(password, salt)
        val userId = "usr_${java.util.UUID.randomUUID().toString().take(12)}"
        val user = RemoteUserAccount(
            userId = userId,
            email = cleanEmail,
            displayName = displayName.ifBlank { cleanEmail.substringBefore("@") },
            passwordHash = passwordHash
        )
        users[cleanEmail] = user
        usersById[userId] = user
        return Result.success(user)
    }

    override suspend fun login(email: String, password: String): Result<RemoteUserAccount> {
        val cleanEmail = email.trim().lowercase()
        val user = users[cleanEmail] ?: return Result.failure(IllegalArgumentException("No account found for $cleanEmail."))
        if (!verifyPassword(password, user.passwordHash)) {
            return Result.failure(SecurityException("Invalid credentials provided."))
        }
        return Result.success(user)
    }

    override suspend fun sendPhoneOtp(phoneNumber: String): Result<String> {
        val cleanPhone = phoneNumber.trim()
        if (cleanPhone.length < 7) {
            return Result.failure(IllegalArgumentException("Invalid phone number format."))
        }
        val verificationId = "ver_${java.util.UUID.randomUUID().toString().take(8)}"
        // Standard test verification code for development / testing
        val code = "739281"
        phoneOtpSessions[verificationId] = Pair(cleanPhone, code)
        return Result.success(verificationId)
    }

    override suspend fun verifyPhoneOtp(verificationId: String, code: String, displayName: String): Result<RemoteUserAccount> {
        val session = phoneOtpSessions[verificationId]
            ?: return Result.failure(IllegalArgumentException("Invalid or expired verification session."))
        if (session.second != code.trim()) {
            return Result.failure(SecurityException("Invalid verification code."))
        }
        phoneOtpSessions.remove(verificationId)
        val phoneNumber = session.first

        val existingUser = usersByPhone[phoneNumber]
        if (existingUser != null) {
            return Result.success(existingUser)
        }

        val userId = "usr_phone_${phoneNumber.hashCode().toUInt()}"
        val user = RemoteUserAccount(
            userId = userId,
            email = "$phoneNumber@phone.fort",
            phoneNumber = phoneNumber,
            displayName = displayName.ifBlank { "Phone User ${phoneNumber.takeLast(4)}" },
            passwordHash = "OTP_AUTHENTICATED"
        )
        usersByPhone[phoneNumber] = user
        usersById[userId] = user
        return Result.success(user)
    }

    override suspend fun loginWithGoogle(idToken: String, displayName: String): Result<RemoteUserAccount> {
        if (idToken.isBlank()) {
            return Result.failure(IllegalArgumentException("ID token is required."))
        }
        val email = "google_user_${idToken.take(8).lowercase()}@gmail.com"
        val existing = users[email]
        if (existing != null) return Result.success(existing)

        val userId = "usr_g_${java.util.UUID.randomUUID().toString().take(10)}"
        val user = RemoteUserAccount(
            userId = userId,
            email = email,
            displayName = displayName.ifBlank { "Google Sovereign User" },
            passwordHash = "FEDERATED_OAUTH_TOKEN"
        )
        users[email] = user
        usersById[userId] = user
        return Result.success(user)
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        val cleanEmail = email.trim().lowercase()
        if (!users.containsKey(cleanEmail)) {
            // Mitigate account enumeration: report success or standard dispatch notification
            return Result.success(Unit)
        }
        return Result.success(Unit)
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

        if (isBlocked(claimantUserId, pass.issuerUserId)) {
            return Result.failure(SecurityException("Server Authorization: Claimant is blocked by pass issuer."))
        }

        // Atomic synchronization on pass record to prevent concurrent double-claim race conditions
        synchronized(pass) {
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

            pass.isClaimed = true
            pass.claimantUserId = claimantUserId
            return Result.success(pass)
        }
    }

    override suspend fun revokePass(passId: String, requesterUserId: String): Result<Unit> {
        val pass = passesById[passId] ?: return Result.failure(IllegalArgumentException("Pass not found."))
        synchronized(pass) {
            if (pass.issuerUserId != requesterUserId) {
                return Result.failure(SecurityException("Server Authorization: Only the pass issuer can revoke access."))
            }
            pass.isRevoked = true
            return Result.success(Unit)
        }
    }

    override suspend fun sendEncryptedPacket(packet: RemoteEncryptedPacket): Result<Unit> {
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

        if (System.currentTimeMillis() >= mood.expiresAt) {
            moods.remove(targetUserId)
            return Result.success(null)
        }

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
        if (!room.adminIds.contains(room.creatorId)) {
            room.adminIds.add(room.creatorId)
        }
        if (!room.members.contains(room.creatorId)) {
            room.members.add(room.creatorId)
        }
        rooms[room.roomId] = room
        return Result.success(Unit)
    }

    override suspend fun fetchRoom(roomId: String, requesterUserId: String): Result<RemoteRoomRecord> {
        val room = rooms[roomId] ?: return Result.failure(IllegalArgumentException("Room not found."))
        if (!room.members.contains(requesterUserId) && !room.adminIds.contains(requesterUserId)) {
            return Result.failure(SecurityException("Server Authorization: Requester is not an enrolled member of this private room."))
        }
        return Result.success(room)
    }

    override suspend fun inviteToRoom(roomId: String, requesterUserId: String, inviteeUserId: String): Result<Unit> {
        val room = rooms[roomId] ?: return Result.failure(IllegalArgumentException("Room not found."))
        if (!room.adminIds.contains(requesterUserId)) {
            return Result.failure(SecurityException("Server Authorization: Only room admins can invite new members."))
        }
        if (!room.members.contains(inviteeUserId)) {
            room.members.add(inviteeUserId)
        }
        return Result.success(Unit)
    }

    override suspend fun removeRoomMember(roomId: String, requesterUserId: String, memberToRemoveId: String): Result<Unit> {
        val room = rooms[roomId] ?: return Result.failure(IllegalArgumentException("Room not found."))
        if (!room.adminIds.contains(requesterUserId)) {
            return Result.failure(SecurityException("Server Authorization: Only room admins can remove members."))
        }
        if (memberToRemoveId == room.creatorId) {
            return Result.failure(SecurityException("Server Authorization: Room creator cannot be removed."))
        }
        room.members.remove(memberToRemoveId)
        room.adminIds.remove(memberToRemoveId)
        return Result.success(Unit)
    }

    override suspend fun leaveRoom(roomId: String, userId: String): Result<Unit> {
        val room = rooms[roomId] ?: return Result.failure(IllegalArgumentException("Room not found."))
        room.members.remove(userId)
        room.adminIds.remove(userId)
        return Result.success(Unit)
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
        usersByPhone.clear()
        passesByToken.clear()
        passesById.clear()
        packets.clear()
        moods.clear()
        rooms.clear()
        blocklists.clear()
        phoneOtpSessions.clear()
    }
}
