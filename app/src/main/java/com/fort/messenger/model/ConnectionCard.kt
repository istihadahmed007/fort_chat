package com.fort.messenger.model

enum class CardType(val displayName: String) {
    PERSONAL("Personal Card"),
    WORK("Work Card"),
    TRAVEL("Travel Card"),
    MARKETPLACE("Marketplace Card")
}

data class ConnectionCard(
    val id: String,
    val type: CardType,
    val displayName: String,
    val handle: String,
    val bio: String,
    val avatarEmoji: String,
    val publicKeyFingerprint: String,
    val moodRingVisible: Boolean,
    val businessHoursOnly: Boolean,
    val activePassCount: Int
)
