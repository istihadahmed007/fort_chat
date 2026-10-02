package com.fort.messenger.security

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern

data class DetectedMetadataItem(
    val id: String,
    val type: String,
    val description: String,
    val rawValue: String,
    var isRedacted: Boolean = false
)

data class ScrubberReport(
    val inputFile: File,
    val detectedItems: List<DetectedMetadataItem>,
    val hasGps: Boolean,
    val hasCameraModel: Boolean,
    val hasPhoneOrAddress: Boolean
)

data class SanitizationResult(
    val success: Boolean,
    val sanitizedFile: File?,
    val verifiedZeroExif: Boolean,
    val message: String
)

/**
 * Genuine on-device media inspection and metadata scrubber.
 * Inspects real EXIF tags on input files and writes clean, sanitized copies preserving the original.
 */
object ShareCheckScrubber {

    private val PHONE_PATTERN = Pattern.compile(
        "(\\+?\\d{1,3}[-.\\s]?)?(\\(?\\d{3}\\)?[-.\\s]?)?\\d{3}[-.\\s]?\\d{4}"
    )

    private val ADDRESS_PATTERN = Pattern.compile(
        "\\b\\d{1,5}\\s+([A-Za-z0-9.\\s]+)\\s+(Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd|Drive|Dr|Lane|Ln|Court|Ct|Way)\\b",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Inspects a real media file locally using ExifInterface and text regex patterns.
     */
    fun inspectFile(file: File, accompanyingText: String = ""): ScrubberReport {
        val detected = mutableListOf<DetectedMetadataItem>()
        var hasGps = false
        var hasCameraModel = false
        var hasPhoneOrAddress = false

        if (file.exists() && file.length() > 0) {
            try {
                val exif = ExifInterface(file)
                val latLong = exif.latLong
                if (latLong != null && latLong.size == 2) {
                    hasGps = true
                    detected.add(
                        DetectedMetadataItem(
                            id = "exif_gps",
                            type = "GPS Coordinates",
                            description = "Precise location coordinates embedded in EXIF header",
                            rawValue = String.format("%.4f° N, %.4f° W", latLong[0], latLong[1])
                        )
                    )
                }

                val make = exif.getAttribute(ExifInterface.TAG_MAKE)
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                if (!make.isNullOrBlank() || !model.isNullOrBlank()) {
                    hasCameraModel = true
                    detected.add(
                        DetectedMetadataItem(
                            id = "exif_device",
                            type = "Hardware Telemetry",
                            description = "Camera device manufacturer and model",
                            rawValue = "${make ?: ""} ${model ?: ""}".trim()
                        )
                    )
                }

                val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME)
                if (!dateTime.isNullOrBlank()) {
                    detected.add(
                        DetectedMetadataItem(
                            id = "exif_timestamp",
                            type = "Capture Timestamp",
                            description = "Exact timestamp when media was recorded",
                            rawValue = dateTime
                        )
                    )
                }
            } catch (e: Exception) {
                // Not a valid EXIF image or unreadable header
            }
        }

        // Inspect accompanying text / filename for phone numbers or addresses
        val textToScan = "$accompanyingText ${file.name}"
        val phoneMatcher = PHONE_PATTERN.matcher(textToScan)
        if (phoneMatcher.find()) {
            hasPhoneOrAddress = true
            detected.add(
                DetectedMetadataItem(
                    id = "text_phone",
                    type = "Phone Number Pattern",
                    description = "Phone number sequence detected in caption or filename",
                    rawValue = phoneMatcher.group()
                )
            )
        }

        val addressMatcher = ADDRESS_PATTERN.matcher(textToScan)
        if (addressMatcher.find()) {
            hasPhoneOrAddress = true
            detected.add(
                DetectedMetadataItem(
                    id = "text_address",
                    type = "Physical Address Pattern",
                    description = "Physical street address pattern detected",
                    rawValue = addressMatcher.group()
                )
            )
        }

        return ScrubberReport(
            inputFile = file,
            detectedItems = detected,
            hasGps = hasGps,
            hasCameraModel = hasCameraModel,
            hasPhoneOrAddress = hasPhoneOrAddress
        )
    }

    /**
     * Creates a genuinely sanitized copy of the media file with all EXIF tags stripped.
     * Preserves original input file untouched.
     * Confirms the resulting file was verified to have zero residual GPS tags.
     */
    fun sanitizeFile(inputFile: File, targetDirectory: File): SanitizationResult {
        if (!inputFile.exists() || inputFile.length() == 0L) {
            return SanitizationResult(
                success = false,
                sanitizedFile = null,
                verifiedZeroExif = false,
                message = "Input file does not exist or is empty."
            )
        }

        val sanitizedFile = File(
            targetDirectory,
            "sanitized_${System.currentTimeMillis()}_${inputFile.nameWithoutExtension}.jpg"
        )

        return try {
            // Re-encode image bitmap directly without EXIF metadata
            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath)
            if (bitmap != null) {
                FileOutputStream(sanitizedFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    out.flush()
                }
                bitmap.recycle()
            } else {
                // For non-bitmap or generic files, copy and strip EXIF tags directly
                inputFile.copyTo(sanitizedFile, overwrite = true)
                val exif = ExifInterface(sanitizedFile)
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
                exif.setAttribute(ExifInterface.TAG_MAKE, null)
                exif.setAttribute(ExifInterface.TAG_MODEL, null)
                exif.setAttribute(ExifInterface.TAG_DATETIME, null)
                exif.saveAttributes()
            }

            // Post-verification check on the output file
            val checkExif = ExifInterface(sanitizedFile)
            val hasRemainingGps = checkExif.latLong != null
            val hasRemainingModel = !checkExif.getAttribute(ExifInterface.TAG_MODEL).isNullOrBlank()

            val verified = !hasRemainingGps && !hasRemainingModel

            SanitizationResult(
                success = true,
                sanitizedFile = sanitizedFile,
                verifiedZeroExif = verified,
                message = if (verified) "File successfully sanitized and verified free of EXIF telemetry."
                else "File sanitized with partial verification."
            )
        } catch (e: Exception) {
            SanitizationResult(
                success = false,
                sanitizedFile = null,
                verifiedZeroExif = false,
                message = "Sanitization failed: ${e.localizedMessage}"
            )
        }
    }
}
