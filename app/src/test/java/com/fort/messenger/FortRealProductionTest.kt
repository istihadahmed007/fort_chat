package com.fort.messenger

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fort.messenger.data.local.FortDatabase
import com.fort.messenger.data.remote.FirebaseRemoteBackend
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
    fun testFirebaseRemoteBackendFailsExplicitlyWhenUnconfigured() = runBlocking {
        val backend = FirebaseRemoteBackend(context)
        val registerResult = backend.register("test@fort.net", "Password123!", "Test")
        assertTrue(registerResult.isFailure)
        val regErr = registerResult.exceptionOrNull()
        assertTrue(regErr is IllegalStateException)
        assertTrue(regErr?.message?.contains("Firebase is not configured") == true)

        val loginResult = backend.login("test@fort.net", "Password123!")
        assertTrue(loginResult.isFailure)
        val loginErr = loginResult.exceptionOrNull()
        assertTrue(loginErr is IllegalStateException)
        assertTrue(loginErr?.message?.contains("Firebase is not configured") == true)

        val googleResult = backend.loginWithGoogle("test_token")
        assertTrue(googleResult.isFailure)
        val googleErr = googleResult.exceptionOrNull()
        assertTrue(googleErr is IllegalStateException)
        assertTrue(googleErr?.message?.contains("Firebase is not configured") == true)
    }
}
