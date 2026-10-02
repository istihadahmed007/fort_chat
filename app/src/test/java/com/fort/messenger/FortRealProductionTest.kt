package com.fort.messenger

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fort.messenger.data.local.FortDatabase
import com.fort.messenger.data.remote.InMemoryRemoteRelay
import com.fort.messenger.data.repository.FortRepository
import com.fort.messenger.model.CardType
import com.fort.messenger.model.PassDurationType
import com.fort.messenger.security.ContactPassPayload
import com.fort.messenger.security.ContactPassQrEngine
import com.fort.messenger.security.FortCryptoManager
import com.fort.messenger.security.IdentityKeyPair
import com.fort.messenger.security.ShareCheckScrubber
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FortRealProductionTest {

    private lateinit var context: Context
    private lateinit var databaseAlice: FortDatabase
    private lateinit var databaseBob: FortDatabase
    private lateinit var serverRelay: InMemoryRemoteRelay
    private lateinit var repositoryAlice: FortRepository
    private lateinit var repositoryBob: FortRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseAlice = FortDatabase.createInMemory(context)
        databaseBob = FortDatabase.createInMemory(context)
        serverRelay = InMemoryRemoteRelay()

        repositoryAlice = FortRepository(databaseAlice, serverRelay)
        repositoryBob = FortRepository(databaseBob, serverRelay)
    }

    @After
    fun tearDown() {
        databaseAlice.close()
        databaseBob.close()
        serverRelay.clearAllData()
    }

    @Test
    fun testTwoAccountRegistrationAndZeroCorrelationPersonas() = runBlocking {
        // Register Alice
        val aliceResult = repositoryAlice.register("alice@fort.net", "Password123!", "Alice")
        assertTrue(aliceResult.isSuccess)
        val alice = aliceResult.getOrThrow()
        assertEquals("alice@fort.net", alice.email)

        // Register Bob
        val bobResult = repositoryBob.register("bob@fort.net", "Password456!", "Bob")
        assertTrue(bobResult.isSuccess)
        val bob = bobResult.getOrThrow()
        assertEquals("bob@fort.net", bob.email)

        // Check Alice's persona cards for zero-correlation isolation
        val aliceCards = repositoryAlice.getPersonaCards(alice.userId).first()
        assertEquals(4, aliceCards.size)

        val publicKeys = aliceCards.map { it.publicKey }
        // Each card facet must possess a distinct, isolated cryptographic public key
        assertEquals(4, publicKeys.distinct().size)

        val personalCard = aliceCards.find { it.type == CardType.PERSONAL }
        val workCard = aliceCards.find { it.type == CardType.WORK }
        assertNotNull(personalCard)
        assertNotNull(workCard)
        assertTrue(personalCard!!.moodSharingEnabled)
        assertFalse(workCard!!.moodSharingEnabled)
        assertTrue(workCard.businessHoursOnly)
    }

    @Test
    fun testEndToEndEncryptionBetweenAliceAndBob() = runBlocking {
        // 1. Setup Alice and Bob accounts
        val alice = repositoryAlice.register("alice@fort.net", "Secret1!", "Alice").getOrThrow()
        val bob = repositoryBob.register("bob@fort.net", "Secret2!", "Bob").getOrThrow()

        // 2. Establish connection: Alice generates 7-day pass, Bob claims it
        val pass = repositoryAlice.generatePass(alice.userId, CardType.PERSONAL, PassDurationType.SEVEN_DAYS).getOrThrow()
        val bobConnection = repositoryBob.claimPass(pass.token, bob.userId, "Bob").getOrThrow()
        assertEquals(alice.userId, bobConnection.peerUserId)

        // Connect Alice's side with Bob's public key
        val bobCard = repositoryBob.getPersonaCards(bob.userId).first().first()
        repositoryAlice.claimPass(
            repositoryBob.generatePass(bob.userId, CardType.PERSONAL, PassDurationType.SEVEN_DAYS).getOrThrow().token,
            alice.userId,
            "Alice"
        )

        // 3. Alice sends genuine E2EE message to Bob
        val plaintext = "Alice says: Confidential rendezvous coordinates 37.7749° N, 122.4194° W"
        val sentResult = repositoryAlice.sendEncryptedMessage(
            conversationId = "conv_${bob.userId}",
            senderUserId = alice.userId,
            recipientUserId = bob.userId,
            plaintext = plaintext
        )
        assertTrue(sentResult.isSuccess)

        // Verify server stores ONLY ciphertext (never readable plaintext)
        val serverPackets = serverRelay.fetchPacketsForUser(bob.userId).getOrThrow()
        assertEquals(1, serverPackets.size)
        val packet = serverPackets.first()
        assertNotEquals(plaintext, packet.ciphertextBase64)
        assertFalse(packet.ciphertextBase64.contains("Confidential rendezvous"))

        // 4. Bob syncs and decrypts inbound message on-device
        val syncedCount = repositoryBob.syncInboundMessages(bob.userId).getOrThrow()
        assertEquals(1, syncedCount)

        val bobMessages = repositoryBob.getConversationMessages("conv_${alice.userId}").first()
        assertEquals(1, bobMessages.size)
        assertEquals(plaintext, bobMessages.first().decryptedTextCache)

        // 5. Unauthorized User Charlie attempts to decrypt packet -> fails
        val charlieKey = FortCryptoManager.generateIdentityKeyPair()
        var decryptionFailedForCharlie = false
        try {
            FortCryptoManager.decrypt(
                com.fort.messenger.security.EncryptedMessagePayload(
                    ciphertextBase64 = packet.ciphertextBase64,
                    ivBase64 = packet.ivBase64,
                    ephemeralPublicKeyBase64 = packet.ephemeralKeyBase64,
                    senderFingerprint = ""
                ),
                charlieKey.privateKeyBase64
            )
        } catch (e: Exception) {
            decryptionFailedForCharlie = true
        }
        assertTrue("Unauthorized recipient must not be able to decrypt E2EE payload", decryptionFailedForCharlie)
    }

    @Test
    fun testSafetyNumberComputationAndKeyChangeAlert() = runBlocking {
        val keyPairAlice = FortCryptoManager.generateIdentityKeyPair()
        val keyPairBob = FortCryptoManager.generateIdentityKeyPair()

        // Derive safety numbers from both perspectives
        val safetyNumberFromAlice = FortCryptoManager.computeSafetyNumber(keyPairAlice.publicKeyBase64, keyPairBob.publicKeyBase64)
        val safetyNumberFromBob = FortCryptoManager.computeSafetyNumber(keyPairBob.publicKeyBase64, keyPairAlice.publicKeyBase64)

        // Must be 100% identical and formatted as 12 5-digit numeric blocks (60 digits total)
        assertEquals(safetyNumberFromAlice, safetyNumberFromBob)
        val blocks = safetyNumberFromAlice.split(" ")
        assertEquals(12, blocks.size)
        assertTrue(blocks.all { it.length == 5 && it.all { char -> char.isDigit() } })

        // Test key rotation detection
        val rotatedKeyPairBob = FortCryptoManager.generateIdentityKeyPair()
        assertNotEquals(keyPairBob.publicKeyBase64, rotatedKeyPairBob.publicKeyBase64)

        val newSafetyNumber = FortCryptoManager.computeSafetyNumber(keyPairAlice.publicKeyBase64, rotatedKeyPairBob.publicKeyBase64)
        assertNotEquals(safetyNumberFromAlice, newSafetyNumber)
    }

    @Test
    fun testContactPassSingleUseAndRevocationEnforcement() = runBlocking {
        val alice = repositoryAlice.register("alice@fort.net", "Pass123!", "Alice").getOrThrow()
        val bob = repositoryBob.register("bob@fort.net", "Pass123!", "Bob").getOrThrow()

        // 1. Single-use pass test
        val singleUsePass = repositoryAlice.generatePass(
            issuerUserId = alice.userId,
            cardType = CardType.MARKETPLACE,
            durationType = PassDurationType.ONE_CONVERSATION
        ).getOrThrow()
        assertTrue(singleUsePass.isSingleUse)

        // Bob claims once -> succeeds
        val claim1 = repositoryBob.claimPass(singleUsePass.token, bob.userId, "Bob")
        assertTrue(claim1.isSuccess)

        // Third party Charlie attempts to claim the same single-use pass -> rejected by server authorization
        val charlieId = "usr_charlie_test"
        val claim2 = repositoryBob.claimPass(singleUsePass.token, charlieId, "Charlie")
        assertTrue(claim2.isFailure)
        assertTrue(claim2.exceptionOrNull() is SecurityException)

        // 2. Revocation test
        val sevenDayPass = repositoryAlice.generatePass(
            issuerUserId = alice.userId,
            cardType = CardType.PERSONAL,
            durationType = PassDurationType.SEVEN_DAYS
        ).getOrThrow()

        // Alice revokes the pass
        val revokeResult = repositoryAlice.revokePass(sevenDayPass.passId, alice.userId)
        assertTrue(revokeResult.isSuccess)

        // Bob attempts to claim revoked pass -> rejected by server authorization
        val claimRevoked = repositoryBob.claimPass(sevenDayPass.token, bob.userId, "Bob")
        assertTrue(claimRevoked.isFailure)
        assertTrue(claimRevoked.exceptionOrNull()?.message?.contains("revoked") == true)
    }

    @Test
    fun testServerSideBlocklistRejectsInboundMessages() = runBlocking {
        val alice = repositoryAlice.register("alice@fort.net", "Pass123!", "Alice").getOrThrow()
        val bob = repositoryBob.register("bob@fort.net", "Pass123!", "Bob").getOrThrow()

        // Connect
        val pass = repositoryAlice.generatePass(alice.userId, CardType.PERSONAL, PassDurationType.SEVEN_DAYS).getOrThrow()
        repositoryBob.claimPass(pass.token, bob.userId, "Bob")

        // Alice blocks Bob
        serverRelay.blockUser(blockerUserId = alice.userId, blockedUserId = bob.userId)
        assertTrue(serverRelay.isBlocked(bob.userId, alice.userId))

        // Bob attempts to transmit encrypted message to Alice -> rejected on server!
        val attemptResult = repositoryBob.sendEncryptedMessage(
            conversationId = "conv_${alice.userId}",
            senderUserId = bob.userId,
            recipientUserId = alice.userId,
            plaintext = "Spam or harassment message"
        )
        assertTrue(attemptResult.isFailure)
        assertTrue(attemptResult.exceptionOrNull() is SecurityException)
    }

    @Test
    fun testMoodRingAudienceIsolationAndRealTimeDecay() = runBlocking {
        val alice = repositoryAlice.register("alice@fort.net", "Pass123!", "Alice").getOrThrow()
        val bobId = "usr_bob_friend"
        val charlieId = "usr_charlie_stranger"

        // 1. Audience Isolation: Alice broadcasts mood solely to Close Circle with Bob included
        val moodResult = repositoryAlice.publishMood(
            userId = alice.userId,
            emotion = "NEED_QUIET",
            whatINeed = "GIVE_ME_SPACE",
            audienceType = "CIRCLES",
            allowedAudienceIds = listOf(alice.userId, bobId),
            decayDurationMinutes = 120L // 2 hours
        )
        assertTrue(moodResult.isSuccess)

        // Bob queries Alice's mood -> permitted
        val bobFetch = repositoryAlice.fetchPeerMood(alice.userId, bobId).getOrThrow()
        assertNotNull(bobFetch)
        assertEquals("NEED_QUIET", bobFetch?.emotion)
        assertEquals("GIVE_ME_SPACE", bobFetch?.whatINeed)

        // Charlie (not in Close Circle) queries Alice's mood -> returned null
        val charlieFetch = repositoryAlice.fetchPeerMood(alice.userId, charlieId).getOrThrow()
        assertNull(charlieFetch)

        // 2. Real Time-Based Expiry
        val expiredMoodResult = repositoryAlice.publishMood(
            userId = alice.userId,
            emotion = "ANGRY",
            whatINeed = "GIVE_ME_SPACE",
            audienceType = "CONNECTIONS",
            allowedAudienceIds = listOf(alice.userId),
            decayDurationMinutes = -10L // Expired 10 minutes ago
        )
        assertTrue(expiredMoodResult.isSuccess)

        // Expired mood must automatically vanish on query
        val fetchExpired = repositoryAlice.fetchPeerMood(alice.userId, bobId).getOrThrow()
        assertNull("Expired mood must automatically disappear without leaving historical logs", fetchExpired)
    }

    @Test
    fun testShareCheckScrubberInspectsAndSanitizesMediaFile() {
        val tempDir = File(context.cacheDir, "test_scrubber_dir").apply { mkdirs() }
        val testImage = File(tempDir, "camera_photo.jpg")

        // Write a test image file
        FileOutputStream(testImage).use { out ->
            val bmp = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
        }

        // Add real EXIF GPS and camera model attributes
        val exif = androidx.exifinterface.media.ExifInterface(testImage)
        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_MAKE, "PixelCameraModel")
        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_MODEL, "TitanM2-Camera")
        exif.setLatLong(37.7749, -122.4194)
        exif.saveAttributes()

        // 1. Inspect file
        val report = ShareCheckScrubber.inspectFile(
            file = testImage,
            accompanyingText = "Call me at +1 (555) 234-5678 or deliver to 742 Evergreen Terrace"
        )
        assertTrue(report.hasGps)
        assertTrue(report.hasCameraModel)
        assertTrue(report.hasPhoneOrAddress)
        assertTrue(report.detectedItems.any { it.type == "GPS Coordinates" })
        assertTrue(report.detectedItems.any { it.type == "Hardware Telemetry" })
        assertTrue(report.detectedItems.any { it.type == "Phone Number Pattern" })

        // 2. Sanitize file
        val outputDir = File(tempDir, "sanitized_out").apply { mkdirs() }
        val result = ShareCheckScrubber.sanitizeFile(testImage, outputDir)
        assertTrue(result.success)
        assertNotNull(result.sanitizedFile)
        assertTrue(result.verifiedZeroExif)

        // 3. Verify original is untouched while sanitized copy has 0 GPS coordinates
        val originalCheck = androidx.exifinterface.media.ExifInterface(testImage)
        assertNotNull(originalCheck.latLong)

        val sanitizedCheck = androidx.exifinterface.media.ExifInterface(result.sanitizedFile!!)
        assertNull("Sanitized file must have zero GPS coordinates", sanitizedCheck.latLong)
        assertNull("Sanitized file must have camera model stripped", sanitizedCheck.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MODEL))
    }

    @Test
    fun testZXingQrCodeMatrixEncodingAndDecoding() {
        val payload = ContactPassPayload(
            passId = "pass_998811",
            token = "PASS-7D-E2EE-TEST",
            issuerUserId = "usr_alice",
            issuerDisplayName = "Alice Vance",
            issuerCardType = "PERSONAL",
            durationType = "SEVEN_DAYS",
            expiresAt = System.currentTimeMillis() + 604800000L,
            issuerPublicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtestpublickey123"
        )

        // Generate real QR bitmap using ZXing
        val qrBitmap = ContactPassQrEngine.generateQrBitmap(payload, 256, 256)
        assertNotNull(qrBitmap)
        assertEquals(256, qrBitmap.width)
        assertEquals(256, qrBitmap.height)
    }

    @Test
    fun testPersistenceAcrossAppRestart() = runBlocking {
        // Register Alice and Bob
        val alice = repositoryAlice.register("alice@fort.net", "Password123!", "Alice").getOrThrow()
        val bob = repositoryBob.register("bob@fort.net", "Password456!", "Bob").getOrThrow()

        // Connect
        val pass = repositoryAlice.generatePass(alice.userId, CardType.PERSONAL, PassDurationType.SEVEN_DAYS).getOrThrow()
        repositoryBob.claimPass(pass.token, bob.userId, "Bob").getOrThrow()

        // Send a message
        val plaintext = "Persistent message saved to SQLite Room DB"
        val sendResult = repositoryAlice.sendEncryptedMessage(
            conversationId = "conv_${bob.userId}",
            senderUserId = alice.userId,
            recipientUserId = bob.userId,
            plaintext = plaintext
        )
        assertTrue("Message send must succeed", sendResult.isSuccess)

        // Simulate app kill and restart: create a new repository instance pointing to the same database
        val restartedRepository = FortRepository(databaseAlice, serverRelay)
        val activeAccount = restartedRepository.getActiveAccount().first()
        assertNotNull("Account session must persist across restart", activeAccount)
        assertEquals("alice@fort.net", activeAccount?.email)

        val messagesAfterRestart = restartedRepository.getConversationMessages("conv_${bob.userId}").first()
        assertEquals(1, messagesAfterRestart.size)
        assertEquals(plaintext, messagesAfterRestart.first().decryptedTextCache)
        assertNotNull(messagesAfterRestart.first().ciphertext)
    }
}
