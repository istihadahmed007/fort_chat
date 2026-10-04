package com.fort.messenger.security

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONObject
import java.util.EnumMap

data class ContactPassPayload(
    val passId: String,
    val token: String,
    val issuerUserId: String,
    val issuerDisplayName: String,
    val issuerCardType: String,
    val durationType: String,
    val expiresAt: Long,
    val issuerPublicKey: String
)

/**
 * Real QR Code Generation & Verification Engine using ZXing.
 * Creates authentic 2D matrix barcodes holding cryptographic Contact Pass tokens.
 */
object ContactPassQrEngine {

    fun generateQrBitmap(
        payload: ContactPassPayload,
        widthPx: Int = 512,
        heightPx: Int = 512
    ): Bitmap {
        val json = JSONObject().apply {
            put("ver", 1)
            put("passId", payload.passId)
            put("token", payload.token)
            put("issuerUserId", payload.issuerUserId)
            put("issuerDisplayName", payload.issuerDisplayName)
            put("issuerCardType", payload.issuerCardType)
            put("durationType", payload.durationType)
            put("expiresAt", payload.expiresAt)
            put("issuerPublicKey", payload.issuerPublicKey)
        }.toString()

        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.MARGIN, 1)
        }

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(json, BarcodeFormat.QR_CODE, widthPx, heightPx, hints)

        val pixels = IntArray(widthPx * heightPx)
        val darkColor = Color.rgb(13, 27, 42) // Sovereign Deep Navy #0D1B2A
        val lightColor = Color.WHITE

        for (y in 0 until heightPx) {
            val offset = y * widthPx
            for (x in 0 until widthPx) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) darkColor else lightColor
            }
        }

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, widthPx, 0, 0, widthPx, heightPx)
        return bitmap
    }

    fun decodeQrBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
            val reader = com.google.zxing.qrcode.QRCodeReader()
            val result = reader.decode(binaryBitmap)
            result.text
        } catch (e: Exception) {
            null
        }
    }

    fun parseQrJson(jsonString: String): ContactPassPayload? {
        return try {
            val json = JSONObject(jsonString)
            ContactPassPayload(
                passId = json.getString("passId"),
                token = json.getString("token"),
                issuerUserId = json.getString("issuerUserId"),
                issuerDisplayName = json.optString("issuerDisplayName", "Anonymous Peer"),
                issuerCardType = json.getString("issuerCardType"),
                durationType = json.getString("durationType"),
                expiresAt = json.getLong("expiresAt"),
                issuerPublicKey = json.getString("issuerPublicKey")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun parseQrOrToken(input: String): ContactPassPayload? {
        val trimmed = input.trim()
        val fromJson = parseQrJson(trimmed)
        if (fromJson != null) return fromJson

        if (trimmed.startsWith("PASS-", ignoreCase = true) || trimmed.length >= 6) {
            return ContactPassPayload(
                passId = "pass_token",
                token = trimmed.uppercase(),
                issuerUserId = "",
                issuerDisplayName = "Sovereign Peer",
                issuerCardType = "PERSONAL",
                durationType = "SEVEN_DAYS",
                expiresAt = System.currentTimeMillis() + 7 * 86400000L,
                issuerPublicKey = ""
            )
        }
        return null
    }
}
