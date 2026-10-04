package com.fort.messenger.data.remote

import android.app.Activity
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Firebase-backed production implementation. InMemoryRemoteRelay remains for tests only.
 * Firebase setup is detected through FirebaseApp resources generated from app/google-services.json.
 */
class FirebaseRemoteBackend(context: Context) : FortRemoteBackend {

    private val appContext = context.applicationContext
    private val firebaseApp: FirebaseApp? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            FirebaseApp.getApps(appContext).firstOrNull() ?: FirebaseApp.initializeApp(appContext)
        }.getOrNull()
    }
    private val automaticPhoneCredential = AtomicReference<PhoneAuthCredential?>(null)

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance(requireFirebaseApp())

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance(requireFirebaseApp())

    private fun requireFirebaseApp(): FirebaseApp {
        return firebaseApp ?: throw IllegalStateException(
            "Firebase is not configured. Add your real google-services.json to the app/ folder, " +
                "enable Authentication providers and Cloud Firestore, then rebuild."
        )
    }

    private fun requireSignedInUserId(): String {
        return auth.currentUser?.uid ?: throw SecurityException("Sign in is required for this action.")
    }

    private fun requireCaller(expectedUserId: String) {
        if (requireSignedInUserId() != expectedUserId) {
            throw SecurityException("The signed-in account cannot perform this action for another user.")
        }
    }

    private suspend fun <T> capture(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun remoteAccount(user: FirebaseUser, preferredName: String = ""): RemoteUserAccount {
        val profileRef = firestore.collection("users").document(user.uid)
        val previous = profileRef.get().await()
        val email = user.email.orEmpty()
        val displayName = preferredName.takeIf { it.isNotBlank() }
            ?: user.displayName?.takeIf { it.isNotBlank() }
            ?: previous.getString("displayName")?.takeIf { it.isNotBlank() }
            ?: email.substringBefore("@").ifBlank { "Fort member" }
        val profile = mutableMapOf<String, Any?>(
            "userId" to user.uid,
            "email" to email,
            "phoneNumber" to user.phoneNumber,
            "displayName" to displayName,
            "updatedAt" to System.currentTimeMillis()
        )
        if (!previous.exists()) profile["createdAt"] = System.currentTimeMillis()
        profileRef.set(profile, SetOptions.merge()).await()
        return RemoteUserAccount(
            userId = user.uid,
            email = email,
            passwordHash = "",
            phoneNumber = user.phoneNumber,
            displayName = displayName
        )
    }

    override suspend fun register(
        email: String,
        password: String,
        displayName: String
    ): Result<RemoteUserAccount> = capture {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: throw IllegalStateException("Firebase created the account without returning a user.")
        if (displayName.isNotBlank()) {
            user.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(displayName.trim()).build()
            ).await()
        }
        remoteAccount(user, displayName.trim())
    }

    override suspend fun login(email: String, password: String): Result<RemoteUserAccount> = capture {
        val user = auth.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: throw IllegalStateException("Firebase sign-in completed without returning a user.")
        remoteAccount(user)
    }

    override suspend fun sendPhoneOtp(phoneNumber: String, activity: Activity?): Result<String> = capture {
        requireFirebaseApp()
        val hostActivity = activity ?: throw IllegalStateException(
            "Phone verification needs an active app screen. Reopen the sign-in screen and try again."
        )
        withTimeout(70_000L) {
            suspendCancellableCoroutine { continuation ->
                val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        automaticPhoneCredential.set(credential)
                        if (continuation.isActive) continuation.resume(AUTO_VERIFIED_SESSION)
                    }

                    override fun onVerificationFailed(error: com.google.firebase.FirebaseException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }

                    override fun onCodeSent(
                        verificationId: String,
                        token: PhoneAuthProvider.ForceResendingToken
                    ) {
                        if (continuation.isActive) continuation.resume(verificationId)
                    }

                    override fun onCodeAutoRetrievalTimeOut(verificationId: String) {
                        if (continuation.isActive) continuation.resume(verificationId)
                    }
                }
                try {
                    val options = PhoneAuthOptions.newBuilder(auth)
                        .setPhoneNumber(phoneNumber.trim())
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(hostActivity)
                        .setCallbacks(callbacks)
                        .build()
                    PhoneAuthProvider.verifyPhoneNumber(options)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        }
    }

    override suspend fun verifyPhoneOtp(
        verificationId: String,
        code: String,
        displayName: String
    ): Result<RemoteUserAccount> = capture {
        val autoCredential = automaticPhoneCredential.getAndSet(null)
        val credential = autoCredential ?: run {
            if (verificationId == AUTO_VERIFIED_SESSION) {
                throw IllegalStateException("Automatic phone verification expired. Request a new code.")
            }
            PhoneAuthProvider.getCredential(verificationId, code.trim())
        }
        val user = auth.signInWithCredential(credential).await().user
            ?: throw IllegalStateException("Phone verification completed without returning a user.")
        remoteAccount(user, displayName.trim())
    }

    override suspend fun loginWithGoogle(
        idToken: String,
        displayName: String
    ): Result<RemoteUserAccount> = capture {
        if (idToken.isBlank()) throw IllegalArgumentException("Google did not return an identity token.")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val user = auth.signInWithCredential(credential).await().user
            ?: throw IllegalStateException("Google sign-in completed without returning a user.")
        remoteAccount(user, displayName.trim())
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = capture {
        auth.sendPasswordResetEmail(email.trim()).await()
        Unit
    }

    override suspend fun signOut(): Result<Unit> = capture {
        if (firebaseApp != null) auth.signOut()
        Unit
    }

    override suspend fun publishPublicKey(
        userId: String,
        cardType: String,
        publicKey: String
    ): Result<Unit> = capture {
        requireCaller(userId)
        firestore.collection("users").document(userId)
            .collection("cards").document(cardType)
            .set(
                mapOf("userId" to userId, "cardType" to cardType, "publicKey" to publicKey),
                SetOptions.merge()
            ).await()
        Unit
    }

    override suspend fun fetchPublicKey(userId: String, cardType: String): Result<String> = capture {
        requireSignedInUserId()
        val snapshot = firestore.collection("users").document(userId)
            .collection("cards").document(cardType).get().await()
        snapshot.getString("publicKey")
            ?: throw IllegalStateException("No public key registered for this Fort card.")
    }

    override suspend fun publishPass(pass: RemotePassRecord): Result<Unit> = capture {
        requireCaller(pass.issuerUserId)
        firestore.collection("contact_passes").document(pass.passId)
            .set(pass.toFirestoreMap()).await()
        Unit
    }

    override suspend fun claimPass(token: String, claimantUserId: String): Result<RemotePassRecord> = capture {
        requireCaller(claimantUserId)
        val query = firestore.collection("contact_passes")
            .whereEqualTo("token", token.trim())
            .limit(1)
            .get()
            .await()
        val reference = query.documents.firstOrNull()?.reference
            ?: throw IllegalArgumentException("Pass not found. Check the invitation and try again.")
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            val pass = snapshot.toPassRecord()
            val now = System.currentTimeMillis()
            if (pass.isRevoked) throw SecurityException("This pass has been revoked.")
            if (pass.expiresAt <= now) throw SecurityException("This pass has expired.")
            if (pass.isSingleUse && pass.isClaimed) throw SecurityException("This single-use pass was already claimed.")
            if (pass.isClaimed && pass.claimantUserId != null && pass.claimantUserId != claimantUserId) {
                throw SecurityException("This pass is already connected to another account.")
            }
            transaction.update(
                reference,
                mapOf("isClaimed" to true, "claimantUserId" to claimantUserId)
            )
            pass.copy(isClaimed = true, claimantUserId = claimantUserId)
        }.await()
    }

    override suspend fun revokePass(passId: String, requesterUserId: String): Result<Unit> = capture {
        requireCaller(requesterUserId)
        val reference = firestore.collection("contact_passes").document(passId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            val pass = snapshot.toPassRecord()
            if (pass.issuerUserId != requesterUserId) {
                throw SecurityException("Only the person who created this pass can revoke it.")
            }
            transaction.update(reference, "isRevoked", true)
            Unit
        }.await()
    }

    override suspend fun sendEncryptedPacket(packet: RemoteEncryptedPacket): Result<Unit> = capture {
        requireCaller(packet.senderUserId)
        firestore.collection("messages").document(packet.packetId)
            .set(packet.toFirestoreMap()).await()
        Unit
    }

    override suspend fun fetchPacketsForUser(recipientUserId: String): Result<List<RemoteEncryptedPacket>> = capture {
        requireCaller(recipientUserId)
        firestore.collection("messages")
            .whereEqualTo("recipientUserId", recipientUserId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toEncryptedPacketOrNull() }
    }

    override suspend fun publishMood(mood: RemoteMoodRecord): Result<Unit> = capture {
        requireCaller(mood.userId)
        firestore.collection("moods").document(mood.userId)
            .set(mood.toFirestoreMap(), SetOptions.merge())
            .await()
        Unit
    }

    override suspend fun fetchMood(
        targetUserId: String,
        requesterUserId: String
    ): Result<RemoteMoodRecord?> = capture {
        requireCaller(requesterUserId)
        val snapshot = firestore.collection("moods").document(targetUserId).get().await()
        if (snapshot.exists()) snapshot.toMoodRecord() else null
    }

    override suspend fun createRoom(room: RemoteRoomRecord): Result<Unit> = capture {
        requireCaller(room.creatorId)
        firestore.collection("rooms").document(room.roomId)
            .set(room.toFirestoreMap()).await()
        Unit
    }

    override suspend fun fetchRoom(roomId: String, requesterUserId: String): Result<RemoteRoomRecord> = capture {
        requireCaller(requesterUserId)
        firestore.collection("rooms").document(roomId).get().await().toRoomRecord()
    }

    override suspend fun inviteToRoom(
        roomId: String,
        requesterUserId: String,
        inviteeUserId: String
    ): Result<Unit> = capture {
        requireCaller(requesterUserId)
        val reference = firestore.collection("rooms").document(roomId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            val data = snapshot.data ?: throw IllegalArgumentException("Room not found.")
            val admins = data.stringList("adminIds")
            if (data["creatorId"] != requesterUserId && requesterUserId !in admins) {
                throw SecurityException("Only room admins can invite members.")
            }
            transaction.update(reference, "members", FieldValue.arrayUnion(inviteeUserId))
            Unit
        }.await()
        Unit
    }

    override suspend fun removeRoomMember(
        roomId: String,
        requesterUserId: String,
        memberToRemoveId: String
    ): Result<Unit> = capture {
        requireCaller(requesterUserId)
        val reference = firestore.collection("rooms").document(roomId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            val data = snapshot.data ?: throw IllegalArgumentException("Room not found.")
            val admins = data.stringList("adminIds")
            if (data["creatorId"] != requesterUserId && requesterUserId !in admins) {
                throw SecurityException("Only room admins can remove members.")
            }
            if (data["creatorId"] == memberToRemoveId) {
                throw SecurityException("The room creator cannot be removed.")
            }
            transaction.update(reference, "members", FieldValue.arrayRemove(memberToRemoveId))
            transaction.update(reference, "adminIds", FieldValue.arrayRemove(memberToRemoveId))
            Unit
        }.await()
        Unit
    }

    override suspend fun leaveRoom(roomId: String, userId: String): Result<Unit> = capture {
        requireCaller(userId)
        val reference = firestore.collection("rooms").document(roomId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            if (!snapshot.exists()) throw IllegalArgumentException("Room not found.")
            transaction.update(reference, "members", FieldValue.arrayRemove(userId))
            transaction.update(reference, "adminIds", FieldValue.arrayRemove(userId))
            Unit
        }.await()
        Unit
    }

    override suspend fun blockUser(blockerUserId: String, blockedUserId: String): Result<Unit> = capture {
        requireCaller(blockerUserId)
        firestore.collection("users").document(blockerUserId)
            .collection("blocklist").document(blockedUserId)
            .set(mapOf("blockedUserId" to blockedUserId, "createdAt" to System.currentTimeMillis()))
            .await()
        Unit
    }

    override suspend fun isBlocked(potentialSenderId: String, targetRecipientId: String): Boolean {
        return try {
            requireSignedInUserId()
            firestore.collection("users").document(targetRecipientId)
                .collection("blocklist").document(potentialSenderId)
                .get().await().exists()
        } catch (_: Exception) {
            true
        }
    }

    private fun RemotePassRecord.toFirestoreMap(): Map<String, Any?> = mapOf(
        "passId" to passId,
        "issuerUserId" to issuerUserId,
        "token" to token,
        "cardType" to cardType,
        "durationType" to durationType,
        "expiresAt" to expiresAt,
        "isSingleUse" to isSingleUse,
        "isClaimed" to isClaimed,
        "isRevoked" to isRevoked,
        "claimantUserId" to claimantUserId,
        "issuerPublicKey" to issuerPublicKey
    )

    private fun DocumentSnapshot.toPassRecord(): RemotePassRecord {
        val data = data ?: throw IllegalArgumentException("Pass record is missing.")
        return RemotePassRecord(
            passId = data["passId"] as? String ?: id,
            issuerUserId = data["issuerUserId"] as? String ?: "",
            token = data["token"] as? String ?: "",
            cardType = data["cardType"] as? String ?: "",
            durationType = data["durationType"] as? String ?: "",
            expiresAt = (data["expiresAt"] as? Number)?.toLong() ?: 0L,
            isSingleUse = data["isSingleUse"] as? Boolean ?: false,
            isClaimed = data["isClaimed"] as? Boolean ?: false,
            isRevoked = data["isRevoked"] as? Boolean ?: false,
            claimantUserId = data["claimantUserId"] as? String,
            issuerPublicKey = data["issuerPublicKey"] as? String ?: ""
        )
    }

    private fun RemoteEncryptedPacket.toFirestoreMap(): Map<String, Any?> = mapOf(
        "packetId" to packetId,
        "senderUserId" to senderUserId,
        "recipientUserId" to recipientUserId,
        "ciphertextBase64" to ciphertextBase64,
        "ivBase64" to ivBase64,
        "ephemeralKeyBase64" to ephemeralKeyBase64,
        "senderSignatureBase64" to senderSignatureBase64,
        "timestamp" to timestamp
    )

    private fun DocumentSnapshot.toEncryptedPacketOrNull(): RemoteEncryptedPacket? {
        val data = data ?: return null
        val sender = data["senderUserId"] as? String ?: return null
        val recipient = data["recipientUserId"] as? String ?: return null
        val ciphertext = data["ciphertextBase64"] as? String ?: return null
        val iv = data["ivBase64"] as? String ?: return null
        val ephemeralKey = data["ephemeralKeyBase64"] as? String ?: return null
        return RemoteEncryptedPacket(
            packetId = data["packetId"] as? String ?: id,
            senderUserId = sender,
            recipientUserId = recipient,
            ciphertextBase64 = ciphertext,
            ivBase64 = iv,
            ephemeralKeyBase64 = ephemeralKey,
            senderSignatureBase64 = data["senderSignatureBase64"] as? String ?: "",
            timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L
        )
    }

    private fun RemoteMoodRecord.toFirestoreMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "emotion" to emotion,
        "whatINeed" to whatINeed,
        "audienceType" to audienceType,
        "allowedAudienceIds" to allowedAudienceIds,
        "expiresAt" to expiresAt
    )

    private fun DocumentSnapshot.toMoodRecord(): RemoteMoodRecord {
        val data = data ?: throw IllegalArgumentException("Mood record is missing.")
        return RemoteMoodRecord(
            userId = data["userId"] as? String ?: id,
            emotion = data["emotion"] as? String ?: "",
            whatINeed = data["whatINeed"] as? String,
            audienceType = data["audienceType"] as? String ?: "PRIVATE",
            allowedAudienceIds = data.stringList("allowedAudienceIds"),
            expiresAt = (data["expiresAt"] as? Number)?.toLong() ?: 0L
        )
    }

    private fun RemoteRoomRecord.toFirestoreMap(): Map<String, Any?> = mapOf(
        "roomId" to roomId,
        "name" to name,
        "creatorId" to creatorId,
        "members" to members.toList(),
        "adminIds" to adminIds.toList(),
        "tasksJson" to tasksJson,
        "expiresAt" to expiresAt
    )

    private fun DocumentSnapshot.toRoomRecord(): RemoteRoomRecord {
        val data = data ?: throw IllegalArgumentException("Room not found.")
        return RemoteRoomRecord(
            roomId = data["roomId"] as? String ?: id,
            name = data["name"] as? String ?: "",
            creatorId = data["creatorId"] as? String ?: "",
            members = data.stringList("members").toMutableList(),
            adminIds = data.stringList("adminIds").toMutableList(),
            tasksJson = data["tasksJson"] as? String ?: "[]",
            expiresAt = (data["expiresAt"] as? Number)?.toLong() ?: 0L
        )
    }

    private fun Map<String, Any?>.stringList(key: String): List<String> =
        (this[key] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

    private fun DocumentSnapshot.stringList(key: String): List<String> =
        (get(key) as? List<*>)?.filterIsInstance<String>() ?: emptyList()

    private companion object {
        const val AUTO_VERIFIED_SESSION = "__AUTO_VERIFIED_PHONE__"
    }
}
