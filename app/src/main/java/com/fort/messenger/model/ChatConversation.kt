package com.fort.messenger.model

data class ChatMessage(
    val id: String,
    val senderName: String,
    val text: String,
    val timestamp: String,
    val isMine: Boolean,
    val isScrubbedMedia: Boolean = false,
    val mediaCaption: String? = null,
    val deliveryStatus: String = "DELIVERED", // PENDING, SENT, DELIVERED, READ, FAILED
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToText: String? = null,
    val reactions: Map<String, Int> = emptyMap(),
    val myReactions: List<String> = emptyList(),
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val attachmentUri: String? = null,
    val attachmentType: String? = null, // "IMAGE", "FILE"
    val attachmentName: String? = null,
    val attachmentSize: Long = 0L,
    val uploadProgress: Float? = null
)

data class ChatConversation(
    val id: String,
    val participantName: String,
    val handle: String,
    val avatarEmoji: String,
    val cardType: CardType,
    val lastMessage: String,
    val lastMessageTime: String,
    val unreadCount: Int,
    val moodEmoji: String? = null,
    val moodWhatINeed: String? = null,
    val passTimeRemaining: String,
    val passType: PassDurationType,
    val isRoom: Boolean = false,
    val isTyping: Boolean = false,
    val isOnline: Boolean = false,
    val lastSeenText: String? = null,
    val messages: List<ChatMessage> = emptyList()
)
