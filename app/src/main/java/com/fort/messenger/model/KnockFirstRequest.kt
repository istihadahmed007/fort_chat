package com.fort.messenger.model

enum class RequestSource(val label: String) {
    QR_CODE("QR Code Scan"),
    ONE_TIME_LINK("One-time Link"),
    MARKETPLACE_PASS("Marketplace Pass")
}

data class KnockFirstRequest(
    val id: String,
    val senderName: String,
    val senderCardType: CardType,
    val source: RequestSource,
    val rawMessageExcerpt: String,
    val sandboxedLink: String? = null,
    val timestamp: String,
    val isCallBlocked: Boolean = true,
    val isMediaBlocked: Boolean = true
)
