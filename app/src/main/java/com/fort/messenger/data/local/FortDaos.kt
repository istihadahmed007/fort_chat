package com.fort.messenger.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserAccountDao {
    @Query("SELECT * FROM user_account LIMIT 1")
    fun getActiveAccount(): Flow<UserAccountEntity?>

    @Query("SELECT * FROM user_account LIMIT 1")
    suspend fun getActiveAccountOnce(): UserAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: UserAccountEntity)

    @Query("UPDATE user_account SET activeCardId = :cardId WHERE userId = :userId")
    suspend fun updateActiveCard(userId: String, cardId: String)

    @Query("UPDATE user_account SET biometricEnabled = :enabled WHERE userId = :userId")
    suspend fun updateBiometric(userId: String, enabled: Boolean)

    @Query("UPDATE user_account SET redactNotifications = :redact WHERE userId = :userId")
    suspend fun updateRedactNotifications(userId: String, redact: Boolean)

    @Query("UPDATE user_account SET showOnlinePresence = :showOnline, showTypingIndicator = :showTyping WHERE userId = :userId")
    suspend fun updateOnlinePrivacy(userId: String, showOnline: Boolean, showTyping: Boolean)

    @Query("DELETE FROM user_account")
    suspend fun clearAccount()
}

@Dao
interface PersonaCardDao {
    @Query("SELECT * FROM persona_cards WHERE userId = :userId")
    fun getCardsForUser(userId: String): Flow<List<PersonaCardEntity>>

    @Query("SELECT * FROM persona_cards WHERE userId = :userId")
    suspend fun getCardsForUserOnce(userId: String): List<PersonaCardEntity>

    @Query("SELECT * FROM persona_cards WHERE cardId = :cardId LIMIT 1")
    suspend fun getCardById(cardId: String): PersonaCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: PersonaCardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<PersonaCardEntity>)

    @Delete
    suspend fun deleteCard(card: PersonaCardEntity)
}

@Dao
interface PeerConnectionDao {
    @Query("SELECT * FROM peer_connections WHERE userId = :userId AND status != 'BLOCKED'")
    fun getActiveConnections(userId: String): Flow<List<PeerConnectionEntity>>

    @Query("SELECT * FROM peer_connections WHERE userId = :userId AND peerUserId = :peerUserId LIMIT 1")
    suspend fun getConnectionWithPeer(userId: String, peerUserId: String): PeerConnectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: PeerConnectionEntity)

    @Query("UPDATE peer_connections SET status = 'BLOCKED' WHERE connectionId = :connectionId")
    suspend fun blockConnection(connectionId: String)

    @Query("UPDATE peer_connections SET isVerified = 1 WHERE connectionId = :connectionId")
    suspend fun verifySafetyNumber(connectionId: String)

    @Query("UPDATE peer_connections SET peerPublicKey = :newKey, keyChangeDetected = 1, isVerified = 0 WHERE connectionId = :connectionId")
    suspend fun reportKeyRotation(connectionId: String, newKey: String)

    @Query("UPDATE peer_connections SET isTyping = :isTyping WHERE connectionId = :connectionId")
    suspend fun updatePeerTyping(connectionId: String, isTyping: Boolean)
}

@Dao
interface KnockFirstDao {
    @Query("SELECT * FROM knock_first_requests WHERE recipientUserId = :userId AND status = 'PENDING' ORDER BY timestamp DESC")
    fun getPendingRequests(userId: String): Flow<List<KnockFirstRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: KnockFirstRequestEntity)

    @Query("UPDATE knock_first_requests SET status = :status WHERE requestId = :requestId")
    suspend fun updateRequestStatus(requestId: String, status: String)

    @Query("DELETE FROM knock_first_requests WHERE requestId = :requestId")
    suspend fun deleteRequest(requestId: String)
}

@Dao
interface ContactPassDao {
    @Query("SELECT * FROM contact_passes WHERE issuerUserId = :userId AND isRevoked = 0")
    fun getActivePasses(userId: String): Flow<List<ContactPassEntity>>

    @Query("SELECT * FROM contact_passes WHERE token = :token LIMIT 1")
    suspend fun getPassByToken(token: String): ContactPassEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPass(pass: ContactPassEntity)

    @Query("UPDATE contact_passes SET isRevoked = 1 WHERE passId = :passId")
    suspend fun revokePass(passId: String)

    @Query("UPDATE contact_passes SET isClaimed = 1, claimantUserId = :claimantUserId WHERE passId = :passId")
    suspend fun markPassClaimed(passId: String, claimantUserId: String)
}

@Dao
interface PrivateRoomDao {
    @Query("SELECT * FROM private_rooms WHERE isClosed = 0")
    fun getActiveRooms(): Flow<List<PrivateRoomEntity>>

    @Query("SELECT * FROM private_rooms WHERE roomId = :roomId LIMIT 1")
    suspend fun getRoomById(roomId: String): PrivateRoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoom(room: PrivateRoomEntity)

    @Query("UPDATE private_rooms SET tasksJson = :tasksJson WHERE roomId = :roomId")
    suspend fun updateRoomTasks(roomId: String, tasksJson: String)

    @Query("UPDATE private_rooms SET membersJson = :membersJson, adminIdsJson = :adminIdsJson WHERE roomId = :roomId")
    suspend fun updateRoomMembers(roomId: String, membersJson: String, adminIdsJson: String)

    @Query("UPDATE private_rooms SET isClosed = 1 WHERE roomId = :roomId")
    suspend fun closeRoom(roomId: String)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessagesFlow(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessageForConversation(conversationId: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages WHERE deliveryStatus = 'PENDING'")
    suspend fun getPendingOutboxMessages(): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): ChatMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Query("UPDATE chat_messages SET deliveryStatus = :status WHERE messageId = :messageId")
    suspend fun updateDeliveryStatus(messageId: String, status: String)

    @Query("UPDATE chat_messages SET reactionsJson = :reactionsJson WHERE messageId = :messageId")
    suspend fun updateMessageReactions(messageId: String, reactionsJson: String)

    @Query("UPDATE chat_messages SET encryptedLocalPayload = :newPayload, decryptedTextCache = :newText, isEdited = 1 WHERE messageId = :messageId")
    suspend fun editMessageContent(messageId: String, newPayload: String, newText: String)

    @Query("UPDATE chat_messages SET isDeleted = 1 WHERE messageId = :messageId")
    suspend fun markMessageDeleted(messageId: String)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteConversationMessages(conversationId: String)

    @Query("UPDATE chat_messages SET deliveryStatus = 'READ' WHERE conversationId = :conversationId AND isMine = 0 AND deliveryStatus != 'READ'")
    suspend fun markMessagesAsReadForConversation(conversationId: String): Int

    @Query("SELECT COUNT(*) FROM chat_messages WHERE conversationId = :conversationId AND isMine = 0 AND deliveryStatus != 'READ'")
    fun getUnreadCountForConversation(conversationId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE conversationId = :conversationId AND isMine = 0 AND deliveryStatus != 'READ'")
    suspend fun getUnreadCount(conversationId: String): Int
}

@Dao
interface MoodRingDao {
    @Query("SELECT * FROM mood_rings WHERE userId = :userId AND expiresAt > :now LIMIT 1")
    fun getActiveMood(userId: String, now: Long = System.currentTimeMillis()): Flow<MoodRingEntity?>

    @Query("SELECT * FROM mood_rings WHERE userId = :userId AND expiresAt > :now LIMIT 1")
    suspend fun getActiveMoodOnce(userId: String, now: Long = System.currentTimeMillis()): MoodRingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMood(mood: MoodRingEntity)

    @Query("DELETE FROM mood_rings WHERE userId = :userId")
    suspend fun clearMood(userId: String)

    @Query("DELETE FROM mood_rings WHERE expiresAt <= :now")
    suspend fun purgeExpiredMoods(now: Long = System.currentTimeMillis())
}
