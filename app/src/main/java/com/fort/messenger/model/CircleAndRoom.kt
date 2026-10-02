package com.fort.messenger.model

data class CircleMember(
    val id: String,
    val name: String,
    val avatarEmoji: String,
    val cardType: CardType,
    val moodEmoji: String? = null
)

data class SharingCircle(
    val id: String,
    val name: String,
    val description: String,
    val iconEmoji: String,
    val isMoodBroadcastEnabled: Boolean,
    val members: List<CircleMember>
)

data class RoomTask(
    val id: String,
    val title: String,
    val isCompleted: Boolean,
    val assignedTo: String
)

data class PrivateRoom(
    val id: String,
    val name: String,
    val purpose: String,
    val iconEmoji: String,
    val expiryRemainingString: String,
    val autoCloseWarning: String,
    val members: List<CircleMember>,
    val tasks: List<RoomTask>
)
