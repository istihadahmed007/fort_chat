package com.fort.messenger.model

enum class SensitivityType(val label: String) {
    EXIF_GPS("GPS Coordinates"),
    PHONE_NUMBER("Phone Number"),
    PHYSICAL_ADDRESS("Physical Address")
}

data class ShareCheckItem(
    val id: String,
    val type: SensitivityType,
    val detectedValue: String,
    val isScrubbed: Boolean = false
) {
    companion object {
        const val SHARE_CHECK_DISCLOSURE =
            "Automatic checks assist privacy but may not detect all sensitive content."
    }
}
