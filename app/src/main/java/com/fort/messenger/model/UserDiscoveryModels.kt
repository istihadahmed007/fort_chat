package com.fort.messenger.model

enum class SearchMode(val label: String) {
    NAME("Name"),
    FORT_ID("Fort ID"),
    PHONE("Phone")
}

data class UserSearchResult(
    val userId: String,
    val displayName: String,
    val fortId: String,
    val avatarEmoji: String = "🛡️",
    val isExistingConnection: Boolean = false,
    val hasVerifiedPhone: Boolean = false,
    val publicKey: String = ""
)

object PhoneDiscoveryHelper {
    /**
     * Normalizes input phone number to E.164 format.
     * Handles international prefix, stripping punctuation, spaces, dashes.
     * Returns null if number format is invalid.
     */
    fun normalizeToE164(raw: String, defaultCountryCode: String = "+1"): String? {
        val cleaned = raw.replace(Regex("[^0-9+]"), "")
        if (cleaned.isBlank()) return null

        val formatted = if (cleaned.startsWith("+")) {
            cleaned
        } else if (cleaned.startsWith("00")) {
            "+" + cleaned.substring(2)
        } else if (cleaned.startsWith("0") && defaultCountryCode == "+880") {
            // Bangladesh local prefix 01XXXXXXXXX -> +8801XXXXXXXXX
            "+880" + cleaned.substring(1)
        } else if (cleaned.length == 10) {
            defaultCountryCode + cleaned
        } else {
            "+" + cleaned
        }

        // Basic E.164 length validation: 8 to 15 digits excluding '+'
        val digitCount = formatted.count { it.isDigit() }
        return if (digitCount in 8..15 && formatted.startsWith("+")) {
            formatted
        } else {
            null
        }
    }
}
