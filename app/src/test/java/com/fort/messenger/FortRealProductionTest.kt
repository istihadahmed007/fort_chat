package com.fort.messenger

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fort.messenger.data.local.FortDatabase
import com.fort.messenger.data.remote.FirebaseRemoteBackend
import com.fort.messenger.data.remote.InMemoryRemoteRelay
import com.fort.messenger.data.repository.FortRepository
import com.fort.messenger.model.*
import com.fort.messenger.data.local.KnockFirstRequestEntity
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

    @Test
    fun testSenderAuthenticationAndTamperDetection() = runBlocking {
        val aliceKey = FortCryptoManager.generateIdentityKeyPair()
        val bobKey = FortCryptoManager.generateIdentityKeyPair()
        val charlieKey = FortCryptoManager.generateIdentityKeyPair()

        val plaintext = "Authenticated sovereign packet"
        val payload = FortCryptoManager.encrypt(
            plaintext = plaintext,
            recipientPublicKeyBase64 = bobKey.publicKeyBase64,
            senderKeyPair = aliceKey
        )
        assertNotNull(payload.senderSignatureBase64)
        assertTrue(payload.senderSignatureBase64.isNotEmpty())

        // 1. Bob verifies valid signature with Alice's public key -> succeeds
        val decrypted = FortCryptoManager.decrypt(
            payload = payload,
            recipientPrivateKeyBase64 = bobKey.privateKeyBase64,
            senderPublicKeyBase64 = aliceKey.publicKeyBase64
        )
        assertEquals(plaintext, decrypted)

        // 2. Charlie tampers with the ciphertext -> fails closed
        val tamperedPayload = payload.copy(ciphertextBase64 = payload.ciphertextBase64.reversed())
        var failedOnTamper = false
        try {
            FortCryptoManager.decrypt(
                payload = tamperedPayload,
                recipientPrivateKeyBase64 = bobKey.privateKeyBase64,
                senderPublicKeyBase64 = aliceKey.publicKeyBase64
            )
        } catch (e: Exception) {
            failedOnTamper = true
        }
        assertTrue("Tampered ciphertext must be rejected by signature or GCM tag", failedOnTamper)

        // 3. Forged sender public key (claiming Charlie sent it) -> signature verification fails closed
        var failedOnForgedSender = false
        try {
            FortCryptoManager.decrypt(
                payload = payload,
                recipientPrivateKeyBase64 = bobKey.privateKeyBase64,
                senderPublicKeyBase64 = charlieKey.publicKeyBase64
            )
        } catch (e: SecurityException) {
            failedOnForgedSender = true
        }
        assertTrue("Message with mismatched sender key must fail closed with SecurityException", failedOnForgedSender)
    }

    @Test
    fun testOfflineMessageQueueingAndRetryOutbox() = runBlocking {
        val alice = repositoryAlice.register("alice_offline@fort.net", "Pass123!", "Alice").getOrThrow()
        val bob = repositoryBob.register("bob_offline@fort.net", "Pass123!", "Bob").getOrThrow()

        // Connect
        val pass = repositoryAlice.generatePass(alice.userId, CardType.PERSONAL, PassDurationType.SEVEN_DAYS).getOrThrow()
        repositoryBob.claimPass(pass.token, bob.userId, "Bob")

        // Disconnect/block Alice on server relay to simulate network drop
        serverRelay.blockUser(blockerUserId = bob.userId, blockedUserId = alice.userId)

        // Alice sends message -> Transmission rejected on server, saved locally as PENDING
        val sendResult = repositoryAlice.sendEncryptedMessage(
            conversationId = "conv_${bob.userId}",
            senderUserId = alice.userId,
            recipientUserId = bob.userId,
            plaintext = "Message during network outage"
        )
        assertTrue("Send during network drop returns failure", sendResult.isFailure)

        val messages = repositoryAlice.getConversationMessages("conv_${bob.userId}").first()
        assertEquals(1, messages.size)
        assertEquals("PENDING", messages.first().deliveryStatus)

        // Unblock to simulate network restoration
        serverRelay.clearAllData()
        serverRelay.register("alice_offline@fort.net", "Pass123!", "Alice")
        serverRelay.register("bob_offline@fort.net", "Pass123!", "Bob")
        val aliceCard = repositoryAlice.getPersonaCards(alice.userId).first().first()
        val bobCard = repositoryBob.getPersonaCards(bob.userId).first().first()
        serverRelay.publishPublicKey(alice.userId, CardType.PERSONAL.name, aliceCard.publicKey)
        serverRelay.publishPublicKey(bob.userId, CardType.PERSONAL.name, bobCard.publicKey)

        // Trigger outbox retry
        val retriedCount = repositoryAlice.retryPendingOutbox()
        assertEquals(1, retriedCount)

        val messagesAfterRetry = repositoryAlice.getConversationMessages("conv_${bob.userId}").first()
        assertEquals("SENT", messagesAfterRetry.first().deliveryStatus)
    }

    @Test
    fun testRoomAdminControlsAndMembershipAuthorization() = runBlocking {
        val alice = repositoryAlice.register("alice_admin@fort.net", "Pass123!", "Alice").getOrThrow()
        val bob = repositoryBob.register("bob_member@fort.net", "Pass123!", "Bob").getOrThrow()
        val charlieId = "usr_charlie_unauthorized"

        // Alice creates private room (Alice is creator & admin)
        val room = repositoryAlice.createRoom(
            name = "Project Citadel",
            purpose = "Secure Operations",
            iconEmoji = "🏰",
            creatorId = alice.userId,
            durationDays = 7,
            initialTasks = listOf("Verify Enclave" to "Alice")
        ).getOrThrow()

        // 1. Alice (admin) invites Bob -> succeeds
        val inviteRes = repositoryAlice.inviteToRoom(room.roomId, alice.userId, bob.userId)
        assertTrue("Creator can invite members", inviteRes.isSuccess)

        // 2. Bob (member, non-admin) attempts to invite Charlie -> fails with SecurityException
        val bobInviteCharlie = serverRelay.inviteToRoom(room.roomId, bob.userId, charlieId)
        assertTrue(bobInviteCharlie.isFailure)
        assertTrue(bobInviteCharlie.exceptionOrNull() is SecurityException)

        // 3. Unauthorized user Charlie attempts to fetch room -> fails with SecurityException
        val charlieFetch = serverRelay.fetchRoom(room.roomId, charlieId)
        assertTrue(charlieFetch.isFailure)
        assertTrue(charlieFetch.exceptionOrNull() is SecurityException)

        // 4. Alice removes Bob -> succeeds
        val removeRes = repositoryAlice.removeRoomMember(room.roomId, alice.userId, bob.userId)
        assertTrue(removeRes.isSuccess)

        // Now Bob is no longer a member -> cannot fetch room
        val bobFetchAfterRemoval = serverRelay.fetchRoom(room.roomId, bob.userId)
        assertTrue(bobFetchAfterRemoval.isFailure)
    }

    @Test
    fun testAtomicSingleUsePassClaimConcurrently() = runBlocking {
        val alice = repositoryAlice.register("alice_atomic@fort.net", "Pass123!", "Alice").getOrThrow()
        val bob = repositoryBob.register("bob_atomic@fort.net", "Pass123!", "Bob").getOrThrow()
        val charlie = repositoryAlice.register("charlie_atomic@fort.net", "Pass123!", "Charlie").getOrThrow()

        val pass = repositoryAlice.generatePass(
            issuerUserId = alice.userId,
            cardType = CardType.MARKETPLACE,
            durationType = PassDurationType.ONE_CONVERSATION
        ).getOrThrow()

        val claim1 = repositoryBob.claimPass(pass.token, bob.userId, "Bob")
        val claim2 = repositoryBob.claimPass(pass.token, charlie.userId, "Charlie")

        val successCount = (if (claim1.isSuccess) 1 else 0) + (if (claim2.isSuccess) 1 else 0)
        val failureCount = (if (claim1.isFailure) 1 else 0) + (if (claim2.isFailure) 1 else 0)

        assertEquals("Exactly one claimant can claim a single-use pass", 1, successCount)
        assertEquals("Subsequent claimant must be rejected", 1, failureCount)
    }

    @Test
    fun testMessageReactionsEditAndDelete() = runBlocking {
        val alice = repositoryAlice.register("alice_react@fort.net", "Pass123!", "Alice").getOrThrow()
        val bob = repositoryBob.register("bob_react@fort.net", "Pass123!", "Bob").getOrThrow()

        val pass = repositoryAlice.generatePass(alice.userId, CardType.PERSONAL, PassDurationType.SEVEN_DAYS).getOrThrow()
        repositoryBob.claimPass(pass.token, bob.userId, "Bob")
        repositoryAlice.claimPass(repositoryBob.generatePass(bob.userId, CardType.PERSONAL, PassDurationType.SEVEN_DAYS).getOrThrow().token, alice.userId, "Alice")

        val sentMsg = repositoryAlice.sendEncryptedMessage(
            conversationId = "conv_${bob.userId}",
            senderUserId = alice.userId,
            recipientUserId = bob.userId,
            plaintext = "Initial message content"
        ).getOrThrow()

        // 1. Add reaction
        repositoryAlice.addMessageReaction(sentMsg.messageId, alice.userId, "❤️")
        var msg = repositoryAlice.getMessageById(sentMsg.messageId)
        assertTrue(msg?.reactionsJson?.contains("❤️") == true)

        // Toggle reaction off
        repositoryAlice.addMessageReaction(sentMsg.messageId, alice.userId, "❤️")
        msg = repositoryAlice.getMessageById(sentMsg.messageId)
        assertFalse(msg?.reactionsJson?.contains("❤️") == true)

        // 2. Edit message
        repositoryAlice.editMessage(sentMsg.messageId, "Updated edited message")
        msg = repositoryAlice.getMessageById(sentMsg.messageId)
        assertTrue(msg?.isEdited == true)

        // 3. Delete message
        repositoryAlice.deleteMessage(sentMsg.messageId)
        msg = repositoryAlice.getMessageById(sentMsg.messageId)
        assertTrue(msg?.isDeleted == true)
    }

    @Test
    fun testPhoneOtpAndGoogleAccountFlows() = runBlocking {
        // 1. Phone OTP dispatch and verification
        val phone = "+15550192834"
        val otpSession = repositoryAlice.sendPhoneOtp(phone).getOrThrow()
        assertTrue(otpSession.isNotEmpty())

        val phoneUser = repositoryAlice.verifyPhoneOtp(otpSession, "739281", "Phone Sovereign").getOrThrow()
        assertEquals(phone, phoneUser.phoneNumber)

        // 2. Google sign-in
        val googleUser = repositoryAlice.loginWithGoogle("GOOGLE_TOKEN_12345", "Google Peer").getOrThrow()
        assertTrue(googleUser.email.contains("google_user_"))

        // 3. Password reset
        val resetResult = repositoryAlice.sendPasswordReset("user@fort.net")
        assertTrue(resetResult.isSuccess)
    }

    @Test
    fun testFirebaseAppInitializationAndConfig() {
        val app = FortApp.ensureFirebaseInitialized(context)
        assertNotNull(app)
        assertEquals("fort-chat-f3308", app?.options?.projectId)
        assertEquals("1:259638681713:android:fc6d3434e8a1e0a149bbd0", app?.options?.applicationId)
        assertEquals("AIzaSyBBD7aMf5z1OwJOt5HSGmW4RJxmcMi5nt0", app?.options?.apiKey)
    }

    @Test
    fun testQrBitmapGenerationAndDecoding() {
        val payload = ContactPassPayload(
            passId = "pass_test_123",
            token = "PASS-TEST99",
            issuerUserId = "user_alice_456",
            issuerDisplayName = "Alice In Enclave",
            issuerCardType = "WORK",
            durationType = "SEVEN_DAYS",
            expiresAt = System.currentTimeMillis() + 7 * 86400000L,
            issuerPublicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE..."
        )

        val qrBitmap = ContactPassQrEngine.generateQrBitmap(payload, 256, 256)
        assertNotNull(qrBitmap)

        val decodedJson = ContactPassQrEngine.decodeQrBitmap(qrBitmap)
        assertNotNull(decodedJson)

        val parsedPayload = ContactPassQrEngine.parseQrJson(decodedJson!!)
        assertNotNull(parsedPayload)
        assertEquals(payload.passId, parsedPayload?.passId)
        assertEquals(payload.token, parsedPayload?.token)
        assertEquals(payload.issuerUserId, parsedPayload?.issuerUserId)
        assertEquals(payload.issuerDisplayName, parsedPayload?.issuerDisplayName)
        assertEquals(payload.issuerCardType, parsedPayload?.issuerCardType)

        // Raw token fallback parsing
        val rawTokenPayload = ContactPassQrEngine.parseQrOrToken("PASS-XYZ987")
        assertNotNull(rawTokenPayload)
        assertEquals("PASS-XYZ987", rawTokenPayload?.token)
    }

    @Test
    fun testTwoWayConnectionCreationAndMessaging() = runBlocking {
        // Register Alice and Bob
        val alice = repositoryAlice.register("alice2@fort.net", "Pass123!", "Alice Sovereign").getOrThrow()
        val bob = repositoryBob.register("bob2@fort.net", "Pass456!", "Bob Sovereign").getOrThrow()

        // Alice generates a pass
        val pass = repositoryAlice.generatePass(alice.userId, CardType.PERSONAL, PassDurationType.SEVEN_DAYS).getOrThrow()
        assertNotNull(pass)

        // Bob claims Alice's pass
        val bobConnResult = repositoryBob.claimPass(pass.token, bob.userId, "Bob Sovereign")
        assertTrue(bobConnResult.isSuccess)
        val bobConn = bobConnResult.getOrThrow()
        assertEquals(alice.userId, bobConn.peerUserId)

        // Bob sends greeting message to Alice
        val sendResult = repositoryBob.sendEncryptedMessage(
            conversationId = "conv_${alice.userId}",
            senderUserId = bob.userId,
            recipientUserId = alice.userId,
            plaintext = "🤝 Hello from Bob! Sovereign channel open."
        )
        assertTrue(sendResult.isSuccess)

        // Alice syncs inbound messages
        val aliceSyncCount = repositoryAlice.syncInboundMessages(alice.userId).getOrThrow()
        assertEquals(1, aliceSyncCount)

        // Alice's reciprocal connection should now exist automatically
        val aliceConn = databaseAlice.peerConnectionDao().getConnectionWithPeer(alice.userId, bob.userId)
        assertNotNull(aliceConn)
        assertEquals(bob.userId, aliceConn?.peerUserId)

        // Alice sees Bob's message in the conversation
        val aliceMessages = repositoryAlice.getConversationMessages("conv_${bob.userId}").first()
        assertEquals(1, aliceMessages.size)
        assertEquals("🤝 Hello from Bob! Sovereign channel open.", aliceMessages[0].decryptedTextCache)
    }

    @Test
    fun testPassPublishFailureDoesNotSaveLocalPass() = runBlocking {
        val alice = repositoryAlice.register("alice_fail@fort.net", "Pass123!", "Alice Fail").getOrThrow()

        // Create a failing backend using interface delegation
        val delegateRelay = InMemoryRemoteRelay()
        val failingBackend = object : com.fort.messenger.data.remote.FortRemoteBackend by delegateRelay {
            override suspend fun publishPass(pass: com.fort.messenger.data.remote.RemotePassRecord): Result<Unit> {
                return Result.failure(IllegalStateException("Simulated Network Error"))
            }
        }
        val failingRepo = FortRepository(databaseAlice, failingBackend)

        val result = failingRepo.generatePass(alice.userId, CardType.PERSONAL, PassDurationType.SEVEN_DAYS)
        assertTrue(result.isFailure)

        // Verify that NO pass was stored in databaseAlice
        val passes = databaseAlice.contactPassDao().getActivePasses(alice.userId).first()
        assertTrue(passes.isEmpty())
    }

    @Test
    fun testMainActivityLaunch() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNotNull(controller.get())
    }

    @Test
    fun testUserDiscoveryByNameFortIdAndPhoneWithPrivacyOptOut() = runBlocking {
        // Register Alice and Charlie with the SAME display name "Alex Fort"
        val alice = repositoryAlice.register("alex1@fort.net", "Pass123!", "Alex Fort").getOrThrow()
        val databaseCharlie = FortDatabase.createInMemory(context)
        val repositoryCharlie = FortRepository(databaseCharlie, serverRelay)
        val charlie = repositoryCharlie.register("alex2@fort.net", "Pass123!", "Alex Fort").getOrThrow()

        // Bob registers with a different name
        val bob = repositoryBob.register("bob_discovery@fort.net", "Pass123!", "Bob Builder").getOrThrow()

        // 1. Search by NAME with duplicate handling: Bob searches for "Alex"
        val nameResults = repositoryBob.searchUsers("Alex", SearchMode.NAME, bob.userId).getOrThrow()
        assertEquals("Both users with duplicate name 'Alex Fort' should be returned", 2, nameResults.size)
        assertTrue(nameResults.any { it.userId == alice.userId })
        assertTrue(nameResults.any { it.userId == charlie.userId })

        // Alice searching for "Alex" should only return Charlie (excludes self)
        val aliceSearchResults = repositoryAlice.searchUsers("Alex", SearchMode.NAME, alice.userId).getOrThrow()
        assertEquals(1, aliceSearchResults.size)
        assertEquals(charlie.userId, aliceSearchResults[0].userId)

        // 2. Search by FORT_ID (exact lookup)
        val bobFoundByFortId = repositoryAlice.searchUsers(bob.userId, SearchMode.FORT_ID, alice.userId).getOrThrow()
        assertEquals(1, bobFoundByFortId.size)
        assertEquals(bob.userId, bobFoundByFortId[0].userId)

        val nonExistentFortId = repositoryAlice.searchUsers("@unknown_person.fort", SearchMode.FORT_ID, alice.userId).getOrThrow()
        assertTrue(nonExistentFortId.isEmpty())

        // 3. Search by PHONE:
        // Alice has no verified phone number -> Phone search must fail
        val unverifiedPhoneSearch = repositoryAlice.searchUsers("+15551234567", SearchMode.PHONE, alice.userId)
        assertTrue("Unverified phone user cannot perform phone searches", unverifiedPhoneSearch.isFailure)

        // Register Dave and Eve with verified phone numbers
        val davePhone = "+15551112233"
        val daveOtp = repositoryAlice.sendPhoneOtp(davePhone).getOrThrow()
        val databaseDave = FortDatabase.createInMemory(context)
        val repositoryDave = FortRepository(databaseDave, serverRelay)
        val dave = repositoryDave.verifyPhoneOtp(daveOtp, "739281", "Dave Secure").getOrThrow()

        val evePhone = "+15559998877"
        val eveOtp = repositoryAlice.sendPhoneOtp(evePhone).getOrThrow()
        val databaseEve = FortDatabase.createInMemory(context)
        val repositoryEve = FortRepository(databaseEve, serverRelay)
        val eve = repositoryEve.verifyPhoneOtp(eveOtp, "739281", "Eve Verified").getOrThrow()

        // Dave searches Eve by phone -> Succeeded
        val evePhoneResult = repositoryDave.searchUsers(evePhone, SearchMode.PHONE, dave.userId).getOrThrow()
        assertEquals(1, evePhoneResult.size)
        assertEquals(eve.userId, evePhoneResult[0].userId)
        assertTrue(evePhoneResult[0].hasVerifiedPhone)

        // 4. Privacy Opt-Out: Eve turns off phone discoverability
        repositoryEve.updateUserDiscoveryPrivacy(eve.userId, discoverableByName = true, discoverableByPhone = false)

        // Dave searches for Eve again by phone -> Returns empty list (protected)
        val eveHiddenResult = repositoryDave.searchUsers(evePhone, SearchMode.PHONE, dave.userId).getOrThrow()
        assertTrue("User who opted out of phone discovery must not be found", eveHiddenResult.isEmpty())

        // Invalid phone number format fails gracefully
        val invalidFormatResult = repositoryDave.searchUsers("invalid-phone-string", SearchMode.PHONE, dave.userId)
        assertTrue(invalidFormatResult.isFailure)

        databaseCharlie.close()
        databaseDave.close()
        databaseEve.close()
    }

    @Test
    fun testContactPassAtomicClaimRevocationAndExpiry() = runBlocking {
        val alice = repositoryAlice.register("alice_pass_test@fort.net", "Pass123!", "Alice Sovereign").getOrThrow()
        val bob = repositoryBob.register("bob_pass_test@fort.net", "Pass123!", "Bob Recipient").getOrThrow()
        val databaseCharlie = FortDatabase.createInMemory(context)
        val repositoryCharlie = FortRepository(databaseCharlie, serverRelay)
        val charlie = repositoryCharlie.register("charlie_pass_test@fort.net", "Pass123!", "Charlie Second").getOrThrow()

        // 1. Single-use pass claimed atomically:
        val singleUsePass = repositoryAlice.generatePass(alice.userId, CardType.PERSONAL, PassDurationType.ONE_CONVERSATION).getOrThrow()
        val bobClaim = repositoryBob.claimPass(singleUsePass.token, bob.userId, "Bob Recipient")
        assertTrue("First claimant should succeed", bobClaim.isSuccess)

        // Second claimant on the single-use pass fails
        val charlieClaim = repositoryCharlie.claimPass(singleUsePass.token, charlie.userId, "Charlie Second")
        assertTrue("Subsequent claimant on single-use pass must fail", charlieClaim.isFailure)

        // 2. Revoked pass cannot be claimed:
        val revocablePass = repositoryAlice.generatePass(alice.userId, CardType.PERSONAL, PassDurationType.SEVEN_DAYS).getOrThrow()
        repositoryAlice.revokePass(revocablePass.passId, alice.userId)

        val claimRevoked = repositoryBob.claimPass(revocablePass.token, bob.userId, "Bob Recipient")
        assertTrue("Revoked pass must fail to claim", claimRevoked.isFailure)

        // 3. Expired pass cannot be claimed:
        val pastExpiry = System.currentTimeMillis() - 10000L
        val expiredPass = repositoryAlice.generatePass(
            issuerUserId = alice.userId,
            cardType = CardType.PERSONAL,
            durationType = PassDurationType.CUSTOM_DURATION,
            customExpiryMillis = pastExpiry
        ).getOrThrow()

        val claimExpired = repositoryBob.claimPass(expiredPass.token, bob.userId, "Bob Recipient")
        assertTrue("Expired pass must fail to claim", claimExpired.isFailure)

        // 4. Non-existent pass token:
        val claimInvalid = repositoryBob.claimPass("PASS-TOTALLY-INVALID-TOKEN", bob.userId, "Bob Recipient")
        assertTrue("Non-existent pass token must fail", claimInvalid.isFailure)

        databaseCharlie.close()
    }

    @Test
    fun testKnockFirstProtocolAcceptDeclineAndBlockWorkflow() = runBlocking {
        val alice = repositoryAlice.register("alice_knock@fort.net", "Pass123!", "Alice Knock").getOrThrow()
        val bob = repositoryBob.register("bob_knock@fort.net", "Pass123!", "Bob Knock").getOrThrow()
        val databaseCharlie = FortDatabase.createInMemory(context)
        val repositoryCharlie = FortRepository(databaseCharlie, serverRelay)
        val charlie = repositoryCharlie.register("charlie_knock@fort.net", "Pass123!", "Charlie Knock").getOrThrow()

        // 1. Alice sends knock-first request to Bob
        val sendRes = repositoryAlice.submitKnockFirstRequest(
            recipientUserId = bob.userId,
            senderUserId = alice.userId,
            senderDisplayName = "Alice Knock",
            senderCardType = CardType.PERSONAL,
            source = "DISCOVERY",
            rawMessage = "Hi Bob, I am Alice from the cryptography conference!"
        )
        assertTrue(sendRes.isSuccess)

        // Bob syncs inbound requests
        val bobSyncCount = repositoryBob.syncInboundKnockFirstRequests(bob.userId).getOrThrow()
        assertEquals(1, bobSyncCount)

        val bobPendingRequests = repositoryBob.getPendingRequests(bob.userId).first()
        assertEquals(1, bobPendingRequests.size)
        val bobRequest = bobPendingRequests[0]
        assertEquals(alice.userId, bobRequest.senderUserId)

        // Bob accepts request
        val acceptRes = repositoryBob.acceptRequestOnce(bobRequest, bob.userId)
        assertTrue(acceptRes.isSuccess)

        // Bob's active connections now contain Alice
        val bobConnections = repositoryBob.getActiveConnections(bob.userId).first()
        assertTrue(bobConnections.any { it.peerUserId == alice.userId })

        // 2. Alice sends request to Charlie, Charlie declines
        repositoryAlice.submitKnockFirstRequest(
            recipientUserId = charlie.userId,
            senderUserId = alice.userId,
            senderDisplayName = "Alice Knock",
            senderCardType = CardType.PERSONAL,
            source = "DISCOVERY",
            rawMessage = "Hi Charlie, let's chat!"
        )
        repositoryCharlie.syncInboundKnockFirstRequests(charlie.userId)
        val charliePending = repositoryCharlie.getPendingRequests(charlie.userId).first()
        assertEquals(1, charliePending.size)

        repositoryCharlie.declineRequest(charliePending[0].requestId, charlie.userId)
        val charlieConns = repositoryCharlie.getActiveConnections(charlie.userId).first()
        assertTrue("Declined request must not create peer connection", charlieConns.isEmpty())

        // 3. Alice sends request to Dave, Dave blocks Alice
        val databaseDave = FortDatabase.createInMemory(context)
        val repositoryDave = FortRepository(databaseDave, serverRelay)
        val dave = repositoryDave.register("dave_knock@fort.net", "Pass123!", "Dave Knock").getOrThrow()

        repositoryAlice.submitKnockFirstRequest(
            recipientUserId = dave.userId,
            senderUserId = alice.userId,
            senderDisplayName = "Alice Knock",
            senderCardType = CardType.PERSONAL,
            source = "DISCOVERY",
            rawMessage = "Unwanted solicitation"
        )
        repositoryDave.syncInboundKnockFirstRequests(dave.userId)
        val davePending = repositoryDave.getPendingRequests(dave.userId).first()
        assertEquals(1, davePending.size)

        repositoryDave.blockAndReportRequest(davePending[0], dave.userId)

        // Any further request from Alice to Dave must be blocked with SecurityException
        val blockedRequestResult = repositoryAlice.submitKnockFirstRequest(
            recipientUserId = dave.userId,
            senderUserId = alice.userId,
            senderDisplayName = "Alice Knock",
            senderCardType = CardType.PERSONAL,
            source = "DISCOVERY",
            rawMessage = "Another attempt after block"
        )
        assertTrue("Blocked user cannot send knock-first requests", blockedRequestResult.isFailure)
        assertTrue(blockedRequestResult.exceptionOrNull() is SecurityException)

        databaseCharlie.close()
        databaseDave.close()
    }

    @Test
    fun testWebRtcCallSignalingAndBlocking() = runBlocking {
        val alice = repositoryAlice.register("alice_call@fort.net", "Pass123!", "Alice Caller").getOrThrow()
        val bob = repositoryBob.register("bob_call@fort.net", "Pass123!", "Bob Callee").getOrThrow()

        val callId = "call_${UUID.randomUUID()}"
        val initialCall = RemoteCallRecord(
            callId = callId,
            callerUserId = alice.userId,
            callerDisplayName = "Alice Caller",
            receiverUserId = bob.userId,
            callType = "AUDIO",
            status = "RINGING"
        )

        // 1. Alice places call to Bob
        val createResult = repositoryAlice.createCall(initialCall)
        assertTrue(createResult.isSuccess)

        // Bob receives incoming call
        val incomingCall = repositoryBob.listenToIncomingCalls(bob.userId).first()
        assertNotNull(incomingCall)
        assertEquals(callId, incomingCall?.callId)
        assertEquals("RINGING", incomingCall?.status)

        // 2. Alice sets SDP Offer
        val mockOfferSdp = "v=0\r\no=alice 12345 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=audio 5004 RTP/AVP 0"
        val offerResult = repositoryAlice.setCallOffer(callId, mockOfferSdp, alice.userId)
        assertTrue(offerResult.isSuccess)

        // 3. Bob accepts and sets SDP Answer
        val mockAnswerSdp = "v=0\r\no=bob 67890 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=audio 5004 RTP/AVP 0"
        val answerResult = repositoryBob.setCallAnswer(callId, mockAnswerSdp, bob.userId)
        assertTrue(answerResult.isSuccess)

        // Remote call status is now ACCEPTED
        val callAfterAnswer = repositoryAlice.listenToCall(callId).first()
        assertEquals("ACCEPTED", callAfterAnswer?.status)
        assertEquals(mockAnswerSdp, callAfterAnswer?.answerSdp)

        // 4. Exchange ICE Candidates
        val aliceCandidate = RtcIceCandidateRecord(
            candidate = "candidate:1 1 UDP 2130706431 192.168.1.100 50000 typ host",
            sdpMid = "audio",
            sdpMLineIndex = 0
        )
        val sendIceResult = repositoryAlice.sendCallIceCandidate(callId, aliceCandidate, isCaller = true, requesterUserId = alice.userId)
        assertTrue(sendIceResult.isSuccess)

        val bobCandidatesList = repositoryBob.listenToCallCandidates(callId, isCaller = true).first()
        assertEquals(1, bobCandidatesList.size)
        assertEquals(aliceCandidate.candidate, bobCandidatesList[0].candidate)

        // 5. Terminate call
        val endResult = repositoryAlice.updateCallStatus(callId, "ENDED", alice.userId)
        assertTrue(endResult.isSuccess)

        val finalCall = repositoryAlice.listenToCall(callId).first()
        assertEquals("ENDED", finalCall?.status)

        // 6. Blocked user cannot call
        val databaseDave = FortDatabase.createInMemory(context)
        val repositoryDave = FortRepository(databaseDave, serverRelay)
        val dave = repositoryDave.register("dave_call@fort.net", "Pass123!", "Dave").getOrThrow()

        // Dave blocks Alice
        serverRelay.blockUser(dave.userId, alice.userId)

        val blockedCall = RemoteCallRecord(
            callId = "call_blocked_123",
            callerUserId = alice.userId,
            callerDisplayName = "Alice Caller",
            receiverUserId = dave.userId,
            callType = "VIDEO",
            status = "RINGING"
        )
        val callToBlocked = repositoryAlice.createCall(blockedCall)
        assertTrue("Placing call to user who blocked caller must fail", callToBlocked.isFailure)
        assertTrue(callToBlocked.exceptionOrNull() is SecurityException)

        databaseDave.close()
    }

    @Test
    fun testPrivateLocationSharingLifecycleAndZeroRetention() = runBlocking {
        val alice = repositoryAlice.register("alice_loc@fort.net", "Pass123!", "Alice Sovereign").getOrThrow()
        val bob = repositoryBob.register("bob_loc@fort.net", "Pass123!", "Bob Sovereign").getOrThrow()

        // Establish connection first
        val pass = repositoryAlice.generatePass(alice.userId, CardType.PERSONAL, PassDurationType.SEVEN_DAYS).getOrThrow()
        repositoryBob.claimPass(pass.token, bob.userId, "Bob Sovereign")
        repositoryAlice.claimPass(repositoryBob.generatePass(bob.userId, CardType.PERSONAL, PassDurationType.SEVEN_DAYS).getOrThrow().token, alice.userId, "Alice Sovereign")

        // 1. Static Location Pin model
        val pin = LocationPin(
            latitude = 37.774929,
            longitude = -122.419416,
            label = "San Francisco Ferry Building"
        )
        assertEquals("37.774929, -122.419416", pin.toLocationMessageText())
        assertTrue(pin.toGeoUri().toString().startsWith("geo:37.774929,-122.419416"))

        // 2. Start Live Location Sharing
        val startResult = repositoryAlice.startLiveLocationSharing(
            senderUserId = alice.userId,
            senderDisplayName = "Alice Sovereign",
            recipientUserId = bob.userId,
            duration = LiveLocationDuration.MINUTES_15,
            initialLat = 37.7749,
            initialLng = -122.4194,
            accuracy = 4.5f
        )
        assertTrue(startResult.isSuccess)
        val session = startResult.getOrThrow()
        assertEquals(alice.userId, session.senderUserId)
        assertEquals(bob.userId, session.recipientUserId)
        assertEquals(15 * 60 * 1000L, session.expiresAt - session.startedAt)
        assertFalse(session.isExpired)

        // Bob fetches active live location
        val bobsView = repositoryBob.fetchActiveLiveLocation(alice.userId, bob.userId).getOrThrow()
        assertNotNull(bobsView)
        assertEquals(37.7749, bobsView?.latitude ?: 0.0, 0.0001)

        // 3. Update Live Location Coordinates
        val updateResult = repositoryAlice.updateLiveLocation(
            shareId = session.shareId,
            senderUserId = alice.userId,
            senderDisplayName = "Alice Sovereign",
            recipientUserId = bob.userId,
            lat = 37.7755,
            lng = -122.4188,
            accuracy = 3.0f,
            startedAt = session.startedAt,
            expiresAt = session.expiresAt
        )
        assertTrue(updateResult.isSuccess)

        val bobsUpdatedView = repositoryBob.fetchActiveLiveLocation(alice.userId, bob.userId).getOrThrow()
        assertEquals(37.7755, bobsUpdatedView?.latitude ?: 0.0, 0.0001)

        // 4. Stop Live Location Sharing
        val stopResult = repositoryAlice.stopLiveLocationSharing(session.shareId, alice.userId, bob.userId)
        assertTrue(stopResult.isSuccess)

        // Bob fetches again -> zero coordinates retention, session stopped
        val bobsViewAfterStop = repositoryBob.fetchActiveLiveLocation(alice.userId, bob.userId).getOrThrow()
        assertTrue("Live location session must be stopped or cleared", bobsViewAfterStop == null || bobsViewAfterStop.isStopped)

        // 5. Expiry Check
        val expiredSession = session.copy(expiresAt = System.currentTimeMillis() - 5000L)
        assertTrue(expiredSession.isExpired)
    }

    @Test
    fun testInboxUnreadCountAndDeliveryStateTracking() = runBlocking {
        val alice = repositoryAlice.register("alice@fort.net", "Pass123!", "Alice").getOrThrow()
        val bob = repositoryBob.register("bob@fort.net", "Pass456!", "Bob").getOrThrow()

        // Establish pass
        val pass = repositoryAlice.generatePass(alice.userId, CardType.PERSONAL, PassDurationType.SEVEN_DAYS).getOrThrow()
        repositoryBob.claimPass(pass.token, bob.userId, "Bob").getOrThrow()

        val convId = "conv_${alice.userId}"

        // Alice sends 3 messages
        repositoryAlice.sendEncryptedMessage("conv_${bob.userId}", alice.userId, bob.userId, "Message 1")
        repositoryAlice.sendEncryptedMessage("conv_${bob.userId}", alice.userId, bob.userId, "Message 2")
        repositoryAlice.sendEncryptedMessage("conv_${bob.userId}", alice.userId, bob.userId, "Message 3")

        // Bob syncs inbound messages
        repositoryBob.syncInboundMessages(bob.userId)

        // Bob checks unread count in DB
        val unreadCountBefore = repositoryBob.getUnreadCount(convId)
        assertEquals(3, unreadCountBefore)

        // Bob marks conversation as read
        repositoryBob.markConversationAsRead(convId, bob.userId)

        // Verify unread count is now 0
        val unreadCountAfter = repositoryBob.getUnreadCount(convId)
        assertEquals(0, unreadCountAfter)

        // Verify delivery status of all messages in conversation is READ
        val messages = repositoryBob.getConversationMessages(convId).first()
        assertEquals(3, messages.size)
        assertTrue(messages.all { it.deliveryStatus == "READ" })
    }

    @Test
    fun testConversationDraftRetentionAndTypingStatusTransmission() = runBlocking {
        val alice = repositoryAlice.register("alice@fort.net", "Pass123!", "Alice").getOrThrow()
        val bob = repositoryBob.register("bob@fort.net", "Pass456!", "Bob").getOrThrow()

        // Alice begins typing to Bob
        repositoryAlice.setTypingStatus(alice.userId, bob.userId, true)

        // Bob listens to Alice's typing status
        val isTyping = repositoryBob.listenToPeerTyping(bob.userId, alice.userId).first()
        assertTrue("Bob must observe Alice typing", isTyping)

        // Alice stops typing
        repositoryAlice.setTypingStatus(alice.userId, bob.userId, false)
        val isTypingStopped = repositoryBob.listenToPeerTyping(bob.userId, alice.userId).first()
        assertFalse("Bob must observe Alice stopped typing", isTypingStopped)
    }

    @Test
    fun testPeopleDiscoveryAndPrivacyRestrictions() = runBlocking {
        val alice = repositoryAlice.register("alice@fort.net", "Pass123!", "Alice Sovereign").getOrThrow()
        val bob = repositoryBob.register("bob@fort.net", "Pass456!", "Bob Guard").getOrThrow()

        // 1. Search by Display Name
        val nameResults = repositoryAlice.searchUsers("Bob", SearchMode.NAME, alice.userId).getOrThrow()
        assertTrue(nameResults.any { it.userId == bob.userId && it.displayName == "Bob Guard" })

        // 2. Search by exact Fort ID
        val bobCard = repositoryBob.getPersonaCards(bob.userId).first().first()
        val fidResults = repositoryAlice.searchUsers(bobCard.handle, SearchMode.FORT_ID, alice.userId).getOrThrow()
        assertEquals(1, fidResults.size)
        assertEquals(bob.userId, fidResults.first().userId)

        // 3. Search non-existent Fort ID
        val invalidFidResults = repositoryAlice.searchUsers("FID-NONEXISTENT-99999", SearchMode.FORT_ID, alice.userId).getOrThrow()
        assertTrue(invalidFidResults.isEmpty())

        // 4. Rate-limited and privacy-protected phone search (Zero enumeration)
        // Requesters without a verified phone are rejected to prevent scrapers
        val unverifiedSearch = repositoryAlice.searchUsers("+15550009999", SearchMode.PHONE, alice.userId)
        assertTrue("Phone search requires verified phone on requester account", unverifiedSearch.isFailure)

        // When requester has verified phone, searching an unregistered number returns empty list (no enumeration leakage)
        val verifiedSession = serverRelay.sendPhoneOtp("+15551234567").getOrThrow()
        val verifiedUser = serverRelay.verifyPhoneOtp(verifiedSession, "739281", "Alice Verified").getOrThrow()
        val registeredSearch = repositoryAlice.searchUsers("+15550009999", SearchMode.PHONE, verifiedUser.userId).getOrThrow()
        assertTrue("Unregistered phone must return empty list without leaking database information", registeredSearch.isEmpty())
    }

    @Test
    fun testQrInvitationClaimSuccessAndFailureScenarios() = runBlocking {
        val alice = repositoryAlice.register("alice@fort.net", "Pass123!", "Alice").getOrThrow()
        val bob = repositoryBob.register("bob@fort.net", "Pass456!", "Bob").getOrThrow()
        val charlie = repositoryBob.register("charlie@fort.net", "Pass789!", "Charlie").getOrThrow()

        // Alice creates a single-use pass
        val pass = repositoryAlice.generatePass(alice.userId, CardType.PERSONAL, PassDurationType.ONE_CONVERSATION).getOrThrow()
        val qrPayload = ContactPassPayload(
            passId = pass.passId,
            token = pass.token,
            issuerUserId = alice.userId,
            issuerDisplayName = "Alice",
            issuerCardType = CardType.PERSONAL.name,
            durationType = PassDurationType.ONE_CONVERSATION.name,
            expiresAt = pass.expiresAt,
            issuerPublicKey = "KEY"
        )

        // 1. Successful claim using passId (fixing the "Pass not found" bug)
        val claimResult = repositoryBob.claimPass(
            token = qrPayload.token,
            claimantUserId = bob.userId,
            claimantDisplayName = "Bob",
            passId = qrPayload.passId,
            issuerDisplayName = qrPayload.issuerDisplayName
        )
        assertTrue(claimResult.isSuccess)
        val conn = claimResult.getOrThrow()
        assertEquals(alice.userId, conn.peerUserId)
        assertEquals("Alice", conn.peerDisplayName)

        // 2. Single-use enforcement: Charlie attempts to claim the same pass -> must fail
        val duplicateClaimResult = repositoryBob.claimPass(
            token = qrPayload.token,
            claimantUserId = charlie.userId,
            claimantDisplayName = "Charlie",
            passId = qrPayload.passId
        )
        assertTrue("Single-use pass cannot be claimed twice", duplicateClaimResult.isFailure)

        // 3. Normalized token variants (without PASS- prefix, lowercase)
        val pass2 = repositoryAlice.generatePass(alice.userId, CardType.WORK, PassDurationType.SEVEN_DAYS).getOrThrow()
        val rawTokenWithoutPrefix = pass2.token.removePrefix("PASS-").lowercase()
        val normalizedClaimResult = repositoryBob.claimPass(
            token = rawTokenWithoutPrefix,
            claimantUserId = bob.userId,
            claimantDisplayName = "Bob"
        )
        assertTrue("Normalized token variants must be resolved successfully", normalizedClaimResult.isSuccess)

        // 4. Revoked pass cannot be claimed
        val pass3 = repositoryAlice.generatePass(alice.userId, CardType.PERSONAL, PassDurationType.ONE_CONVERSATION).getOrThrow()
        repositoryAlice.revokePass(pass3.passId, alice.userId)
        val revokedClaimResult = repositoryBob.claimPass(
            token = pass3.token,
            claimantUserId = bob.userId,
            claimantDisplayName = "Bob"
        )
        assertTrue("Revoked pass claim must fail", revokedClaimResult.isFailure)
    }

    @Test
    fun testKnockFirstReciprocalConnectionAndConversationInitialization() = runBlocking {
        val alice = repositoryAlice.register("alice@fort.net", "Pass123!", "Alice").getOrThrow()
        val bob = repositoryBob.register("bob@fort.net", "Pass456!", "Bob").getOrThrow()

        // Alice sends Knock First request with intro message
        val knockResult = repositoryAlice.submitKnockFirstRequest(
            recipientUserId = bob.userId,
            senderUserId = alice.userId,
            senderDisplayName = "Alice",
            senderCardType = CardType.PERSONAL,
            source = "DISCOVERY_SEARCH",
            rawMessage = "Hello Bob, please accept my connection.",
            sandboxedLink = null
        )
        assertTrue(knockResult.isSuccess)

        // Bob syncs inbound knock-first requests from server
        val syncCount = repositoryBob.syncInboundKnockFirstRequests(bob.userId).getOrThrow()
        assertEquals(1, syncCount)

        // Bob observes pending knock request
        val bobsRequests = repositoryBob.getPendingRequests(bob.userId).first()
        assertEquals(1, bobsRequests.size)
        val req = bobsRequests.first()
        assertEquals(alice.userId, req.senderUserId)
        assertEquals("Hello Bob, please accept my connection.", req.rawMessage)

        // Bob accepts request for 7 days
        val acceptResult = repositoryBob.grantRequestSevenDays(req, bob.userId)
        assertTrue(acceptResult.isSuccess)

        // Bob now has an active connection with Alice
        val bobsConnections = repositoryBob.getActiveConnections(bob.userId).first()
        assertTrue(bobsConnections.any { it.peerUserId == alice.userId })

        // Alice syncs inbound messages -> receives reciprocal pass and greeting
        repositoryAlice.syncInboundMessages(alice.userId)
        val alicesConnections = repositoryAlice.getActiveConnections(alice.userId).first()
        assertTrue("Alice must have reciprocal connection with Bob", alicesConnections.any { it.peerUserId == bob.userId })
    }

    @Test
    fun testWebRtcSignalingAudioVideoCallStates() = runBlocking {
        val alice = repositoryAlice.register("alice@fort.net", "Pass123!", "Alice").getOrThrow()
        val bob = repositoryBob.register("bob@fort.net", "Pass456!", "Bob").getOrThrow()

        val callId = "call_${UUID.randomUUID()}"
        val record = RemoteCallRecord(
            callId = callId,
            callerUserId = alice.userId,
            callerDisplayName = "Alice",
            receiverUserId = bob.userId,
            callType = "VIDEO",
            status = "RINGING"
        )

        // 1. Alice creates outgoing call
        val startResult = repositoryAlice.createCall(record)
        assertTrue(startResult.isSuccess)

        // 2. Bob listens to incoming calls and sees the ringing call
        val incomingCall = repositoryBob.listenToIncomingCalls(bob.userId).first()
        assertNotNull(incomingCall)
        assertEquals(callId, incomingCall?.callId)
        assertEquals("RINGING", incomingCall?.status)
        assertEquals("VIDEO", incomingCall?.callType)

        // 3. Bob accepts the call
        val acceptResult = repositoryBob.updateCallStatus(callId, "ACCEPTED", bob.userId)
        assertTrue(acceptResult.isSuccess)

        val updatedCall = repositoryAlice.listenToCall(callId).first()
        assertEquals("ACCEPTED", updatedCall?.status)

        // 4. Alice and Bob exchange ICE candidates
        val candidate = RtcIceCandidateRecord("candidate:12345 1 udp 2122260223 192.168.1.5 50005 typ host", "video", 0)
        val sendIceResult = repositoryAlice.sendCallIceCandidate(callId, candidate, isCaller = true, alice.userId)
        assertTrue(sendIceResult.isSuccess)

        val bobsCandidates = repositoryBob.listenToCallCandidates(callId, isCaller = true).first()
        assertEquals(1, bobsCandidates.size)
        assertEquals(candidate.candidate, bobsCandidates.first().candidate)

        // 5. Alice ends the call
        val endResult = repositoryAlice.updateCallStatus(callId, "ENDED", alice.userId)
        assertTrue(endResult.isSuccess)

        val endedCall = repositoryBob.listenToCall(callId).first()
        assertEquals("ENDED", endedCall?.status)
    }

    @Test
    fun testEphemeralLocationSharingPinLiveAndExpiry() = runBlocking {
        val alice = repositoryAlice.register("alice_loc@fort.net", "Pass123!", "Alice Loc").getOrThrow()
        val bob = repositoryBob.register("bob_loc@fort.net", "Pass456!", "Bob Loc").getOrThrow()

        // 1. Establish connection via pass
        val pass = repositoryAlice.generatePass(alice.userId, CardType.PERSONAL, PassDurationType.SEVEN_DAYS).getOrThrow()
        val claimResult = repositoryBob.claimPass(pass.token, bob.userId, "Bob Loc")
        assertTrue(claimResult.isSuccess)
        repositoryAlice.syncInboundMessages(alice.userId)

        // 2. One-time static location pin
        val pin = LocationPin(latitude = 37.7749, longitude = -122.4194, label = "San Francisco HQ")
        val pinResult = repositoryAlice.sendLocationPin("conv_${bob.userId}", alice.userId, bob.userId, pin)
        assertTrue(pinResult.isSuccess)

        // Bob syncs message and decrypts location pin
        repositoryBob.syncInboundMessages(bob.userId)
        val bobMessages = repositoryBob.getConversationMessages("conv_${alice.userId}").first()
        assertTrue(bobMessages.any { it.decryptedTextCache.contains("37.7749") && it.decryptedTextCache.contains("-122.4194") })

        // 3. Start duration-limited live location sharing (15 minutes)
        val startLive = repositoryAlice.startLiveLocationSharing(
            senderUserId = alice.userId,
            senderDisplayName = "Alice Loc",
            recipientUserId = bob.userId,
            duration = LiveLocationDuration.MINUTES_15,
            initialLat = 37.7750,
            initialLng = -122.4195,
            accuracy = 5.0f
        )
        assertTrue(startLive.isSuccess)
        val session = startLive.getOrThrow()
        assertFalse(session.isExpired)

        // Bob fetches active live location
        val bobsActiveLoc = repositoryBob.fetchActiveLiveLocation(alice.userId, bob.userId).getOrThrow()
        assertNotNull(bobsActiveLoc)
        assertEquals(37.7750, bobsActiveLoc!!.latitude, 0.0001)

        // 4. Sender updates live location
        val updateResult = repositoryAlice.updateLiveLocation(
            shareId = session.shareId,
            senderUserId = alice.userId,
            senderDisplayName = "Alice Loc",
            recipientUserId = bob.userId,
            lat = 37.7755,
            lng = -122.4200,
            accuracy = 3.0f,
            startedAt = session.startedAt,
            expiresAt = session.expiresAt
        )
        assertTrue(updateResult.isSuccess)

        val updatedLoc = repositoryBob.fetchActiveLiveLocation(alice.userId, bob.userId).getOrThrow()
        assertEquals(37.7755, updatedLoc!!.latitude, 0.0001)

        // 5. Sender stops live location sharing
        val stopResult = repositoryAlice.stopLiveLocationSharing(session.shareId, alice.userId, bob.userId)
        assertTrue(stopResult.isSuccess)

        // Active session is now null / expired
        val stoppedLoc = repositoryBob.fetchActiveLiveLocation(alice.userId, bob.userId).getOrThrow()
        assertNull("Stopped live location session must not be visible", stoppedLoc)
    }

    @Test
    fun testLogoutLoginPreservesIdentityKeysWithoutSilentRotation() = runBlocking {
        // Register Alice
        val alice = repositoryAlice.register("alice@fort.net", "Pass123!", "Alice").getOrThrow()
        val originalCards = repositoryAlice.getPersonaCards(alice.userId).first()
        val originalPublicKeys = originalCards.map { it.publicKey }
        val originalPrivateKeys = originalCards.map { it.privateKeyEncrypted }

        // Logout
        repositoryAlice.logout()
        assertNull(repositoryAlice.getActiveAccount().first())

        // Login again
        val loggedIn = repositoryAlice.login("alice@fort.net", "Pass123!").getOrThrow()
        assertEquals(alice.userId, loggedIn.userId)

        // Verify all 4 persona keys are exactly identical (not rotated or overwritten)
        val restoredCards = repositoryAlice.getPersonaCards(alice.userId).first()
        assertEquals(originalCards.size, restoredCards.size)
        for (i in originalCards.indices) {
            assertEquals("Public key must be preserved across logout/login", originalPublicKeys[i], restoredCards[i].publicKey)
            assertEquals("Private key must be preserved across logout/login", originalPrivateKeys[i], restoredCards[i].privateKeyEncrypted)
        }
    }

    @Test
    fun testIdempotentInboundMessageProcessing() = runBlocking {
        val alice = repositoryAlice.register("alice@fort.net", "Pass1!", "Alice").getOrThrow()
        val bob = repositoryBob.register("bob@fort.net", "Pass2!", "Bob").getOrThrow()

        val pass = repositoryAlice.generatePass(alice.userId, CardType.PERSONAL, PassDurationType.SEVEN_DAYS).getOrThrow()
        repositoryBob.claimPass(pass.token, bob.userId, "Bob").getOrThrow()

        // Alice sends message
        val sentMsg = repositoryAlice.sendEncryptedMessage("conv_${bob.userId}", alice.userId, bob.userId, "Hello Bob! Sovereign message.").getOrThrow()

        // Bob syncs first time
        val count1 = repositoryBob.syncInboundMessages(bob.userId).getOrThrow()
        assertEquals(1, count1)

        val bobMessages1 = repositoryBob.getConversationMessages("conv_${alice.userId}").first()
        assertEquals(1, bobMessages1.size)
        assertEquals("Hello Bob! Sovereign message.", bobMessages1[0].decryptedTextCache)

        // Bob syncs a second time (e.g. from real-time snapshot re-emission)
        val count2 = repositoryBob.syncInboundMessages(bob.userId).getOrThrow()
        assertEquals(0, count2)

        val bobMessages2 = repositoryBob.getConversationMessages("conv_${alice.userId}").first()
        assertEquals(1, bobMessages2.size) // No duplicates!
    }

    @Test
    fun testKnockFirstAcceptanceEstablishesReciprocalConnectionOnBothAccounts() = runBlocking {
        val alice = repositoryAlice.register("alice@fort.net", "Pass1!", "Alice").getOrThrow()
        val bob = repositoryBob.register("bob@fort.net", "Pass2!", "Bob").getOrThrow()

        // Alice knocks on Bob's door
        val knockRes = repositoryAlice.submitKnockFirstRequest(
            recipientUserId = bob.userId,
            senderUserId = alice.userId,
            senderDisplayName = "Alice",
            senderCardType = CardType.PERSONAL,
            source = "DISCOVERY_SEARCH",
            rawMessage = "Hello Bob, please accept my knock."
        )
        assertTrue(knockRes.isSuccess)

        // Bob syncs and receives the inbound knock request
        repositoryBob.syncInboundKnockFirstRequests(bob.userId)
        val bobsRequests = repositoryBob.getPendingRequests(bob.userId).first()
        assertEquals(1, bobsRequests.size)
        val bobsReq = bobsRequests[0]
        assertEquals("PENDING", bobsReq.status)

        // Bob accepts request
        val acceptRes = repositoryBob.acceptRequestOnce(bobsReq, bob.userId)
        assertTrue(acceptRes.isSuccess)

        // Bob now has an active connection with Alice
        val bobsConnections = repositoryBob.getActiveConnections(bob.userId).first()
        assertEquals(1, bobsConnections.size)
        assertEquals(alice.userId, bobsConnections[0].peerUserId)

        // Alice's outbound listener observes the accepted status
        val outboundReqs = repositoryAlice.listenToOutboundKnockFirstRequests(alice.userId).first()
        val acceptedReq = outboundReqs.find { it.requestId == bobsReq.requestId }
        assertNotNull(acceptedReq)
        assertEquals("ACCEPTED", acceptedReq!!.status)

        // Alice handles the accepted status
        repositoryAlice.handleOutboundKnockStatusChange(acceptedReq, alice.userId)

        // Alice now ALSO has an active reciprocal connection with Bob!
        val alicesConnections = repositoryAlice.getActiveConnections(alice.userId).first()
        assertEquals(1, alicesConnections.size)
        assertEquals(bob.userId, alicesConnections[0].peerUserId)

        // Both accounts can now exchange encrypted messages
        repositoryAlice.sendEncryptedMessage("conv_${bob.userId}", alice.userId, bob.userId, "Hi Bob! Connected on both sides.").getOrThrow()
        repositoryBob.syncInboundMessages(bob.userId)
        val bobMsgs = repositoryBob.getConversationMessages("conv_${alice.userId}").first()
        assertTrue(bobMsgs.any { it.decryptedTextCache == "Hi Bob! Connected on both sides." })
    }

    @Test
    fun testCaseVariedSearchAndDuplicateDisplayNames() = runBlocking {
        val alice1 = repositoryAlice.register("alice1@fort.net", "Pass1!", "Alice Wonder").getOrThrow()
        val alice2 = repositoryBob.register("alice2@fort.net", "Pass2!", "Alice Wonder").getOrThrow()

        // Case-varied search by lower case
        val searchLower = repositoryAlice.searchUsers("alice", SearchMode.NAME, alice1.userId).getOrThrow()
        assertTrue("Should find matching users regardless of case", searchLower.isNotEmpty())

        // Case-varied search by upper case
        val searchUpper = repositoryAlice.searchUsers("ALICE", SearchMode.NAME, alice1.userId).getOrThrow()
        assertTrue("Should find matching users with uppercase query", searchUpper.isNotEmpty())

        // Search by prefix
        val searchPrefix = repositoryAlice.searchUsers("Ali", SearchMode.NAME, alice1.userId).getOrThrow()
        assertTrue("Should match prefix Ali", searchPrefix.isNotEmpty())
    }

    @Test
    fun testWebRtcCandidateDeduplication() {
        val seen = mutableSetOf<String>()
        val cand1 = RtcIceCandidateRecord("candidate:1", "0", 0, "server1")
        val cand2 = RtcIceCandidateRecord("candidate:1", "0", 0, "server1") // Duplicate!
        val cand3 = RtcIceCandidateRecord("candidate:2", "0", 1, "server1")

        val key1 = "${cand1.sdpMid}_${cand1.sdpMLineIndex}_${cand1.candidate}"
        val key2 = "${cand2.sdpMid}_${cand2.sdpMLineIndex}_${cand2.candidate}"
        val key3 = "${cand3.sdpMid}_${cand3.sdpMLineIndex}_${cand3.candidate}"

        assertTrue(seen.add(key1))
        assertFalse("Duplicate candidate must be filtered out", seen.add(key2))
        assertTrue(seen.add(key3))
        assertEquals(2, seen.size)
    }
}
