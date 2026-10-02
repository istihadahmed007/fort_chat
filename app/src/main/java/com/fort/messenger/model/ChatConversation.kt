package com.fort.messenger.model

data class ChatMessage(
    val id: String,
    val senderName: String,
    val text: String,
    val timestamp: String,
    val isMine: Boolean,
    val isScrubbedMedia: Boolean = false,
    val mediaCaption: String? = null
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
    val messages: List<ChatMessage> = emptyList()
)
