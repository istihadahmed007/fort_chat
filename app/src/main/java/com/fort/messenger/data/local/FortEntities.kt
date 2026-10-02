package com.fort.messenger.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fort.messenger.model.CardType
import com.fort.messenger.model.PassDurationType

@Entity(tableName = "user_account")
data class UserAccountEntity(
    @PrimaryKey val userId: String,
    val email: String,
    val authToken: String,
    val activeCardId: String,
    val biometricEnabled: Boolean = false,
    val redactNotifications: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "persona_cards")
data class PersonaCardEntity(
    @PrimaryKey val cardId: String,
    val userId: String,
    val type: CardType,
    val displayName: String,
    val handle: String,
    val bio: String,
    val avatarUri: String? = null,
    val avatarEmoji: String = "🛡️",
    val publicKey: String,
    val privateKeyEncrypted: String,
    val businessHoursOnly: Boolean = false,
    val moodSharingEnabled: Boolean = true
)

@Entity(tableName = "peer_connections")
data class PeerConnectionEntity(
    @PrimaryKey val connectionId: String,
    val userId: String,
    val peerUserId: String,
    val peerDisplayName: String,
    val peerHandle: String,
    val peerAvatarEmoji: String = "👤",
    val peerCardType: CardType,
    val peerPublicKey: String,
    val safetyNumber: String,
    val isVerified: Boolean = false,
    val keyChangeDetected: Boolean = false,
    val passType: PassDurationType,
    val passExpiresAt: Long,
    val status: String = "ACTIVE" // ACTIVE, BLOCKED, EXPIRED
)

@Entity(tableName = "knock_first_requests")
data class KnockFirstRequestEntity(
    @PrimaryKey val requestId: String,
    val recipientUserId: String,
    val senderUserId: String,
    val senderDisplayName: String,
    val senderCardType: CardType,
    val source: String, // QR_CODE, ONE_TIME_LINK, MARKETPLACE_PASS
    val rawMessage: String,
    val sandboxedLink: String? = null,
    val timestamp: String,
    val status: String = "PENDING" // PENDING, ACCEPTED, DECLINED, BLOCKED
)

@Entity(tableName = "contact_passes")
data class ContactPassEntity(
    @PrimaryKey val passId: String,
    val issuerUserId: String,
    val token: String,
    val cardType: CardType,
    val durationType: PassDurationType,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long,
    val isSingleUse: Boolean = false,
    val isClaimed: Boolean = false,
    val isRevoked: Boolean = false
)

@Entity(tableName = "private_rooms")
data class PrivateRoomEntity(
    @PrimaryKey val roomId: String,
    val name: String,
    val purpose: String,
    val iconEmoji: String,
    val creatorId: String,
    val membersJson: String,
    val tasksJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long,
    val isClosed: Boolean = false
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val messageId: String,
    val conversationId: String,
    val senderUserId: String,
    val recipientUserId: String,
    val ciphertext: String,
    val iv: String,
    val ephemeralKey: String,
    val decryptedTextCache: String, // Decrypted on-device only
    val timestamp: Long,
    val isMine: Boolean,
    val isScrubbedMedia: Boolean = false,
    val deliveryStatus: String = "DELIVERED" // PENDING, SENT, DELIVERED, FAILED
)

@Entity(tableName = "mood_rings")
data class MoodRingEntity(
    @PrimaryKey val userId: String,
    val emotion: String, // ANGRY, HAPPY, SAD, STRESSED, READY_TO_TALK, NEED_QUIET
    val whatINeed: String?,
    val audienceType: String, // PRIVATE, CONNECTIONS, SELECTED_PEOPLE, CIRCLES
    val allowedAudienceIdsJson: String, // JSON array of user IDs or circle names
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long
)
