package com.fort.messenger.model

enum class PassDurationType(val label: String, val description: String) {
    ONE_CONVERSATION("One Conversation", "Closes when discussion wraps or becomes idle"),
    SEVEN_DAYS("7 Days", "Ephemeral pass for short projects or visits"),
    CUSTOM_DURATION("Custom Duration", "Specified epoch validity window"),
    ONGOING("Ongoing", "Active until manually revoked in Access Map")
}

enum class PassStatus {
    ACTIVE,
    EXPIRING_SOON,
    REVOKED,
    CLOSED
}

data class ContactPass(
    val id: String,
    val token: String,
    val cardType: CardType,
    val durationType: PassDurationType,
    val status: PassStatus,
    val counterpartyName: String,
    val timeRemainingString: String,
    val expiryTimestamp: Long,
    val isRevocable: Boolean = true
) {
    companion object {
        const val REVOCATION_DISCLOSURE =
            "Revoking access prevents future messages through this pass. It does not remotely delete messages or media already stored on the recipient's device."
    }
}
