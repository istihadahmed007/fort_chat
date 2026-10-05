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
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import com.fort.messenger.data.local.KnockFirstRequestEntity
import com.fort.messenger.model.*
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
        com.fort.messenger.FortApp.ensureFirebaseInitialized(appContext)
    }
    private val automaticPhoneCredential = AtomicReference<PhoneAuthCredential?>(null)

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance(requireFirebaseApp())

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance(requireFirebaseApp())

    private fun requireFirebaseApp(): FirebaseApp {
        return firebaseApp ?: com.fort.messenger.FortApp.ensureFirebaseInitialized(appContext) ?: throw IllegalStateException(
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
        val fortId = previous.getString("fortId")
            ?: "@${displayName.lowercase().replace(" ", "").replace("@", "")}.fort"
        val discoverableByName = previous.getBoolean("discoverableByName") ?: true
        val discoverableByPhone = previous.getBoolean("discoverableByPhone") ?: true
        val profile = mutableMapOf<String, Any?>(
            "userId" to user.uid,
            "email" to email,
            "phoneNumber" to user.phoneNumber,
            "displayName" to displayName,
            "fortId" to fortId,
            "discoverableByName" to discoverableByName,
            "discoverableByPhone" to discoverableByPhone,
            "updatedAt" to System.currentTimeMillis()
        )
        if (!previous.exists()) profile["createdAt"] = System.currentTimeMillis()
        profileRef.set(profile, SetOptions.merge()).await()
        return RemoteUserAccount(
            userId = user.uid,
            email = email,
            passwordHash = "",
            phoneNumber = user.phoneNumber,
            displayName = displayName,
            fortId = fortId,
            discoverableByName = discoverableByName,
            discoverableByPhone = discoverableByPhone
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

    override suspend fun sendPhoneOtp(phoneNumber: String, activity: Activity?): Result<String> {
        automaticPhoneCredential.set(null)
        return try {
            capture {
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
        } catch (_: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Phone verification timed out. Check the number and try again."))
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
        val cardDoc = firestore.collection("users").document(userId)
            .collection("cards").document(cardType).get().await()
        cardDoc.getString("publicKey") ?: run {
            val allCards = firestore.collection("users").document(userId)
                .collection("cards").get().await()
            allCards.documents.firstNotNullOfOrNull { it.getString("publicKey") }
                ?: throw IllegalStateException("No public key registered for this Fort card.")
        }
    }

    override suspend fun publishPass(pass: RemotePassRecord): Result<Unit> = capture {
        requireCaller(pass.issuerUserId)
        firestore.collection("contact_passes").document(pass.passId)
            .set(pass.toFirestoreMap()).await()
        Unit
    }

    override suspend fun claimPass(token: String, claimantUserId: String, passId: String?): Result<RemotePassRecord> = capture {
        requireCaller(claimantUserId)
        val cleanToken = token.trim().uppercase()
        val tokenVariants = listOf(
            cleanToken,
            token.trim(),
            cleanToken.removePrefix("PASS-"),
            if (!cleanToken.startsWith("PASS-")) "PASS-$cleanToken" else cleanToken
        ).distinct()

        var reference = if (!passId.isNullOrBlank()) {
            val doc = firestore.collection("contact_passes").document(passId).get().await()
            if (doc.exists()) doc.reference else null
        } else null

        if (reference == null) {
            for (t in tokenVariants) {
                val query = firestore.collection("contact_passes")
                    .whereEqualTo("token", t)
                    .limit(1)
                    .get()
                    .await()
                val found = query.documents.firstOrNull()?.reference
                if (found != null) {
                    reference = found
                    break
                }
            }
        }

        if (reference == null) {
            // Also check by passId equality in field
            val queryByPassId = firestore.collection("contact_passes")
                .whereEqualTo("passId", token.trim())
                .limit(1)
                .get()
                .await()
            reference = queryByPassId.documents.firstOrNull()?.reference
        }

        val passRef = reference ?: throw IllegalArgumentException("Pass not found. Check the invitation and try again.")
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(passRef)
            val pass = snapshot.toPassRecord()
            val now = System.currentTimeMillis()
            if (pass.isRevoked) throw SecurityException("This pass has been revoked.")
            if (pass.expiresAt <= now) throw SecurityException("This pass has expired.")
            if (pass.isSingleUse && pass.isClaimed) throw SecurityException("This single-use pass was already claimed.")
            if (pass.isClaimed && pass.claimantUserId != null && pass.claimantUserId != claimantUserId) {
                throw SecurityException("This pass is already connected to another account.")
            }
            transaction.update(
                passRef,
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
        try {
            val snapshot = firestore.collection("moods").document(targetUserId).get().await()
            if (snapshot.exists()) snapshot.toMoodRecord() else null
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                null
            } else {
                throw e
            }
        }
    }

    override suspend fun createRoom(room: RemoteRoomRecord): Result<Unit> = capture {
        requireCaller(room.creatorId)
        if (room.creatorId !in room.members) {
            room.members.add(room.creatorId)
        }
        if (room.creatorId !in room.adminIds) {
            room.adminIds.add(room.creatorId)
        }
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
            val data = snapshot.data ?: throw IllegalArgumentException("Room not found.")
            val members = data.stringList("members")
            if (userId !in members) throw IllegalArgumentException("You are not a member of this room.")
            transaction.update(reference, "members", FieldValue.arrayRemove(userId))
            if (userId in data.stringList("adminIds")) {
                transaction.update(reference, "adminIds", FieldValue.arrayRemove(userId))
            }
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
            false
        }
    }

    override suspend fun searchUsers(
        query: String,
        mode: SearchMode,
        requesterUserId: String
    ): Result<List<UserSearchResult>> = capture {
        requireCaller(requesterUserId)
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@capture emptyList()

        when (mode) {
            SearchMode.NAME -> {
                val snapshot = firestore.collection("users")
                    .whereGreaterThanOrEqualTo("displayName", trimmed)
                    .whereLessThanOrEqualTo("displayName", trimmed + "\uf8ff")
                    .limit(20)
                    .get()
                    .await()

                snapshot.documents.mapNotNull { doc ->
                    val uid = doc.getString("userId") ?: doc.id
                    val dName = doc.getString("displayName") ?: return@mapNotNull null
                    val discoverable = doc.getBoolean("discoverableByName") ?: true
                    if (uid == requesterUserId || !discoverable) return@mapNotNull null

                    val fortId = doc.getString("fortId") ?: "@${dName.lowercase().replace(" ", "")}.fort"
                    UserSearchResult(
                        userId = uid,
                        displayName = dName,
                        fortId = fortId,
                        avatarEmoji = "🛡️",
                        isExistingConnection = false,
                        hasVerifiedPhone = doc.getString("phoneNumber") != null
                    )
                }
            }
            SearchMode.FORT_ID -> {
                val cleanFortId = if (trimmed.startsWith("@")) trimmed else "@$trimmed"
                val baseFortId = cleanFortId.substringBefore(".").lowercase()
                var snapshot = firestore.collection("users")
                    .whereEqualTo("fortId", cleanFortId.lowercase())
                    .limit(1)
                    .get()
                    .await()

                if (snapshot.isEmpty) {
                    snapshot = firestore.collection("users")
                        .whereEqualTo("fortId", "$baseFortId.fort")
                        .limit(1)
                        .get()
                        .await()
                }

                val doc = snapshot.documents.firstOrNull() ?: firestore.collection("users").document(trimmed).get().await()
                if (!doc.exists()) emptyList()
                else {
                    val uid = doc.getString("userId") ?: doc.id
                    val dName = doc.getString("displayName") ?: "Sovereign User"
                    if (uid == requesterUserId) emptyList()
                    else listOf(
                        UserSearchResult(
                            userId = uid,
                            displayName = dName,
                            fortId = doc.getString("fortId") ?: cleanFortId,
                            avatarEmoji = "🛡️",
                            isExistingConnection = false,
                            hasVerifiedPhone = doc.getString("phoneNumber") != null
                        )
                    )
                }
            }
            SearchMode.PHONE -> {
                val myPhone = auth.currentUser?.phoneNumber
                if (myPhone.isNullOrBlank()) {
                    throw IllegalStateException("Phone verification is required before you can discover peers by phone.")
                }
                val normalized = PhoneDiscoveryHelper.normalizeToE164(trimmed)
                    ?: throw IllegalArgumentException("Invalid phone number format. Include country code (e.g. +1234567890).")

                val snapshot = firestore.collection("users")
                    .whereEqualTo("phoneNumber", normalized)
                    .limit(1)
                    .get()
                    .await()

                val doc = snapshot.documents.firstOrNull()
                if (doc == null || !doc.exists()) emptyList()
                else {
                    val uid = doc.getString("userId") ?: doc.id
                    val discoverable = doc.getBoolean("discoverableByPhone") ?: true
                    val dName = doc.getString("displayName") ?: "Sovereign User"
                    if (uid == requesterUserId || !discoverable) emptyList()
                    else listOf(
                        UserSearchResult(
                            userId = uid,
                            displayName = dName,
                            fortId = doc.getString("fortId") ?: "@${dName.lowercase().replace(" ", "")}.fort",
                            avatarEmoji = "🛡️",
                            isExistingConnection = false,
                            hasVerifiedPhone = true
                        )
                    )
                }
            }
        }
    }

    override suspend fun fetchUserProfile(userId: String): Result<UserSearchResult?> = capture {
        val doc = firestore.collection("users").document(userId).get().await()
        if (!doc.exists()) return@capture null
        val dName = doc.getString("displayName") ?: "Sovereign User"
        val fortId = doc.getString("fortId") ?: "@${dName.lowercase().replace(" ", "")}.fort"
        UserSearchResult(
            userId = userId,
            displayName = dName,
            fortId = fortId,
            avatarEmoji = "🛡️",
            isExistingConnection = false,
            hasVerifiedPhone = doc.getString("phoneNumber") != null
        )
    }

    override suspend fun setTypingStatus(userId: String, recipientUserId: String, isTyping: Boolean): Result<Unit> = capture {
        requireCaller(userId)
        val presenceRef = firestore.collection("users").document(userId)
            .collection("presence").document("typing")
        val data = mapOf(
            "recipientUserId" to recipientUserId,
            "isTyping" to isTyping,
            "updatedAt" to System.currentTimeMillis()
        )
        presenceRef.set(data, SetOptions.merge()).await()
        Unit
    }

    override fun listenToTypingStatus(recipientUserId: String, senderUserId: String): Flow<Boolean> = callbackFlow {
        val presenceRef = firestore.collection("users").document(senderUserId)
            .collection("presence").document("typing")
        val registration = presenceRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(false)
                return@addSnapshotListener
            }
            val target = snapshot.getString("recipientUserId")
            val isTyping = snapshot.getBoolean("isTyping") ?: false
            val updatedAt = snapshot.getLong("updatedAt") ?: 0L
            val isRecent = (System.currentTimeMillis() - updatedAt) < 5000L
            trySend(target == recipientUserId && isTyping && isRecent)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun updateDeliveryStatus(messageId: String, status: String, recipientUserId: String): Result<Unit> = capture {
        requireCaller(recipientUserId)
        val msgRef = firestore.collection("messages").document(messageId)
        val updateMap = mutableMapOf<String, Any>("deliveryStatus" to status)
        if (status == "READ") {
            updateMap["readAt"] = System.currentTimeMillis()
        }
        msgRef.update(updateMap).await()
        Unit
    }

    override suspend fun updateUserDiscoveryPrivacy(
        userId: String,
        discoverableByName: Boolean,
        discoverableByPhone: Boolean
    ): Result<Unit> = capture {
        requireCaller(userId)
        firestore.collection("users").document(userId).update(
            mapOf(
                "discoverableByName" to discoverableByName,
                "discoverableByPhone" to discoverableByPhone,
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
        Unit
    }

    override suspend fun submitKnockFirstRequest(request: KnockFirstRequestEntity): Result<Unit> = capture {
        requireCaller(request.senderUserId)
        firestore.collection("knock_first").document(request.requestId).set(
            mapOf(
                "requestId" to request.requestId,
                "senderUserId" to request.senderUserId,
                "recipientUserId" to request.recipientUserId,
                "senderDisplayName" to request.senderDisplayName,
                "senderCardType" to request.senderCardType.name,
                "source" to request.source,
                "rawMessage" to request.rawMessage,
                "sandboxedLink" to request.sandboxedLink,
                "timestamp" to request.timestamp,
                "status" to request.status
            )
        ).await()
        Unit
    }

    override suspend fun fetchKnockFirstRequests(recipientUserId: String): Result<List<KnockFirstRequestEntity>> = capture {
        requireCaller(recipientUserId)
        val snapshot = firestore.collection("knock_first")
            .whereEqualTo("recipientUserId", recipientUserId)
            .whereEqualTo("status", "PENDING")
            .get()
            .await()
        snapshot.documents.mapNotNull { doc ->
            val reqId = doc.getString("requestId") ?: doc.id
            val sender = doc.getString("senderUserId") ?: return@mapNotNull null
            val senderName = doc.getString("senderDisplayName") ?: "Peer"
            val cardType = try {
                CardType.valueOf(doc.getString("senderCardType") ?: "PERSONAL")
            } catch (_: Exception) { CardType.PERSONAL }
            KnockFirstRequestEntity(
                requestId = reqId,
                recipientUserId = recipientUserId,
                senderUserId = sender,
                senderDisplayName = senderName,
                senderCardType = cardType,
                source = doc.getString("source") ?: "FORT_ID",
                rawMessage = doc.getString("rawMessage") ?: "",
                sandboxedLink = doc.getString("sandboxedLink"),
                timestamp = doc.getString("timestamp") ?: System.currentTimeMillis().toString(),
                status = doc.getString("status") ?: "PENDING"
            )
        }
    }

    override suspend fun updateKnockFirstStatus(
        requestId: String,
        status: String,
        recipientUserId: String
    ): Result<Unit> = capture {
        requireCaller(recipientUserId)
        firestore.collection("knock_first").document(requestId)
            .update("status", status)
            .await()
        Unit
    }

    override suspend fun createCall(call: RemoteCallRecord): Result<Unit> = capture {
        requireCaller(call.callerUserId)
        firestore.collection("calls").document(call.callId).set(
            mapOf(
                "callId" to call.callId,
                "callerUserId" to call.callerUserId,
                "callerDisplayName" to call.callerDisplayName,
                "receiverUserId" to call.receiverUserId,
                "callType" to call.callType,
                "status" to call.status,
                "offerSdp" to call.offerSdp,
                "answerSdp" to call.answerSdp,
                "createdAt" to call.createdAt,
                "updatedAt" to call.updatedAt
            )
        ).await()
        Unit
    }

    override suspend fun updateCallStatus(callId: String, status: String, requesterUserId: String): Result<Unit> = capture {
        requireSignedInUserId()
        firestore.collection("calls").document(callId).update(
            mapOf(
                "status" to status,
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
        Unit
    }

    override suspend fun setCallOffer(callId: String, sdp: String, requesterUserId: String): Result<Unit> = capture {
        requireSignedInUserId()
        firestore.collection("calls").document(callId).update(
            mapOf(
                "offerSdp" to sdp,
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
        Unit
    }

    override suspend fun setCallAnswer(callId: String, sdp: String, requesterUserId: String): Result<Unit> = capture {
        requireSignedInUserId()
        firestore.collection("calls").document(callId).update(
            mapOf(
                "answerSdp" to sdp,
                "status" to "ACCEPTED",
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
        Unit
    }

    override suspend fun sendCallIceCandidate(
        callId: String,
        candidate: RtcIceCandidateRecord,
        isCaller: Boolean,
        requesterUserId: String
    ): Result<Unit> = capture {
        requireSignedInUserId()
        firestore.collection("calls").document(callId).collection("candidates").add(
            mapOf(
                "candidate" to candidate.candidate,
                "sdpMid" to candidate.sdpMid,
                "sdpMLineIndex" to candidate.sdpMLineIndex,
                "isCaller" to isCaller,
                "timestamp" to System.currentTimeMillis()
            )
        ).await()
        Unit
    }

    override fun listenToCall(callId: String): Flow<RemoteCallRecord?> = callbackFlow {
        val listener = firestore.collection("calls").document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val record = RemoteCallRecord(
                    callId = snapshot.getString("callId") ?: snapshot.id,
                    callerUserId = snapshot.getString("callerUserId") ?: "",
                    callerDisplayName = snapshot.getString("callerDisplayName") ?: "Peer",
                    receiverUserId = snapshot.getString("receiverUserId") ?: "",
                    callType = snapshot.getString("callType") ?: "AUDIO",
                    status = snapshot.getString("status") ?: "RINGING",
                    offerSdp = snapshot.getString("offerSdp"),
                    answerSdp = snapshot.getString("answerSdp"),
                    createdAt = snapshot.getLong("createdAt") ?: 0L,
                    updatedAt = snapshot.getLong("updatedAt") ?: 0L
                )
                trySend(record)
            }
        awaitClose { listener.remove() }
    }

    override fun listenToIncomingCalls(userId: String): Flow<RemoteCallRecord?> = callbackFlow {
        val listener = firestore.collection("calls")
            .whereEqualTo("receiverUserId", userId)
            .whereEqualTo("status", "RINGING")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.isEmpty) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val doc = snapshot.documents.firstOrNull()
                if (doc != null) {
                    val record = RemoteCallRecord(
                        callId = doc.getString("callId") ?: doc.id,
                        callerUserId = doc.getString("callerUserId") ?: "",
                        callerDisplayName = doc.getString("callerDisplayName") ?: "Peer",
                        receiverUserId = doc.getString("receiverUserId") ?: "",
                        callType = doc.getString("callType") ?: "AUDIO",
                        status = doc.getString("status") ?: "RINGING",
                        offerSdp = doc.getString("offerSdp"),
                        answerSdp = doc.getString("answerSdp"),
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        updatedAt = doc.getLong("updatedAt") ?: 0L
                    )
                    trySend(record)
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    override fun listenToCallCandidates(callId: String, isCaller: Boolean): Flow<List<RtcIceCandidateRecord>> = callbackFlow {
        val listener = firestore.collection("calls").document(callId).collection("candidates")
            .whereEqualTo("isCaller", isCaller)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot.documents.mapNotNull { d ->
                    val cand = d.getString("candidate") ?: return@mapNotNull null
                    val mid = d.getString("sdpMid") ?: ""
                    val line = (d.get("sdpMLineIndex") as? Number)?.toInt() ?: 0
                    RtcIceCandidateRecord(candidate = cand, sdpMid = mid, sdpMLineIndex = line)
                }
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun publishLiveLocation(session: LiveLocationSession): Result<Unit> = capture {
        requireCaller(session.senderUserId)
        firestore.collection("live_locations").document(session.shareId).set(
            mapOf(
                "shareId" to session.shareId,
                "senderUserId" to session.senderUserId,
                "senderDisplayName" to session.senderDisplayName,
                "recipientUserId" to session.recipientUserId,
                "latitude" to session.latitude,
                "longitude" to session.longitude,
                "accuracyMeters" to session.accuracyMeters,
                "startedAt" to session.startedAt,
                "expiresAt" to session.expiresAt,
                "isStopped" to session.isStopped,
                "lastUpdated" to session.lastUpdated
            ),
            SetOptions.merge()
        ).await()
        Unit
    }

    override suspend fun stopLiveLocation(shareId: String, requesterUserId: String): Result<Unit> = capture {
        requireSignedInUserId()
        firestore.collection("live_locations").document(shareId).update(
            mapOf(
                "isStopped" to true,
                "lastUpdated" to System.currentTimeMillis()
            )
        ).await()
        Unit
    }

    override suspend fun fetchActiveLiveLocation(senderUserId: String, recipientUserId: String): Result<LiveLocationSession?> = capture {
        requireSignedInUserId()
        val now = System.currentTimeMillis()
        val snapshot = firestore.collection("live_locations")
            .whereEqualTo("senderUserId", senderUserId)
            .whereEqualTo("recipientUserId", recipientUserId)
            .whereEqualTo("isStopped", false)
            .whereGreaterThan("expiresAt", now)
            .limit(1)
            .get()
            .await()

        val doc = snapshot.documents.firstOrNull() ?: return@capture null
        LiveLocationSession(
            shareId = doc.getString("shareId") ?: doc.id,
            senderUserId = doc.getString("senderUserId") ?: "",
            senderDisplayName = doc.getString("senderDisplayName") ?: "Contact",
            recipientUserId = doc.getString("recipientUserId") ?: "",
            latitude = doc.getDouble("latitude") ?: 0.0,
            longitude = doc.getDouble("longitude") ?: 0.0,
            accuracyMeters = (doc.get("accuracyMeters") as? Number)?.toFloat() ?: 0f,
            startedAt = doc.getLong("startedAt") ?: 0L,
            expiresAt = doc.getLong("expiresAt") ?: 0L,
            isStopped = doc.getBoolean("isStopped") ?: false,
            lastUpdated = doc.getLong("lastUpdated") ?: 0L
        )
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
