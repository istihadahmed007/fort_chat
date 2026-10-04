# FORT — Sovereign Identity, Bounded Access & Private Messaging

> *"Your identity. Your circle. Your rules."*

**Platform:** Native Android (Kotlin / Jetpack Compose)  
**Design System:** `Sovereign Sanctuary` (Light / Dark High-Contrast Theme, Deep Slate `#0A0F1D`, Royal Blue `#2563EB`, Ice Blue `#E0E7FF`, Emerald `#10B981`, Rose `#EF4444`)  
**Architecture:** MVI / Clean Architecture with Coroutines StateFlow & SQLite Room Database  
**Cryptography:** NIST P-256 ECDH Key Agreement, NIST P-256 ECDSA Digital Signatures (`SHA256withECDSA`), HKDF-SHA256, AES-256-GCM Authenticated Encryption, Android KeyStore Hardware Master Key, PBKDF2 Password Hashing

---

## 1. Overview & Sovereign Sanctuary Philosophy

FORT is a native Android private messaging application engineered to eradicate the structural privacy and social failures of legacy telephone-number-based messengers:
* **Context Collapse:** Eliminated via **Zero-Correlation Connection Cards** (Personal, Work, Travel, Marketplace), where each facet possesses an isolated cryptographic key pair, avatar, handle, and bio. Senders never discover whether a counterparty maintains other persona cards.
* **Persistent Indefinite Exposure:** Replaced by **Bounded Contact Passes** (`One Conversation`, `7 Days`, `Custom Duration`, `Ongoing`) featuring atomic single-use enforcement and deterministic server-side revocation.
* **Knock First Protocol:** Quarantines inbound unverified communications. Senders cannot call, transmit voice notes, or trigger media downloads prior to verification. External links are rendered as plain-text without background crawler leaks.
* **Emotional Availability Misalignment:** Solved by the **Mood Ring & Quiet Presence** micro-badge with manual emotion selection (`ANGRY`, `HAPPY`, `SAD`, `STRESSED`, `READY_TO_TALK`, `NEED_QUIET`), actionable layers, circle-gated audience boundaries, and automatic time-based decay.
* **Honest Privacy Architecture:** Transparent disclosures without deceptive "100% Unhackable" marketing claims. Decrypted message bodies and private keys are never stored as plaintext in SQLite database files. Zero synthetic accounts, pre-seeded contacts, or fake cloud syncs.

---

## 2. Security Architecture & Genuine End-to-End Encryption

### 2.1 Cryptographic Suite
FORT uses maintained, established industry cryptography:
1. **Key Agreement & Forward Secrecy:** Ephemeral Elliptic Curve Diffie-Hellman (ECDH) over the NIST P-256 (`secp256r1`) curve generated per packet.
2. **Sender Identity Authentication:** Senders sign outbound packets `(ephemeralPublicKey + ciphertext + iv)` using NIST P-256 ECDSA digital signatures (`SHA256withECDSA`) with their private identity key. Recipients verify signatures against the sender's verified public identity key before decrypting; forged or tampered packets fail closed with `SecurityException`.
3. **Key Derivation:** HKDF-SHA256 derives 256-bit symmetric encryption keys with salt and info binding.
4. **Authenticated Cipher:** AES-256-GCM with a unique 96-bit Initialization Vector (IV) generated per packet via `SecureRandom`.
5. **Hardware-Enclave Master Key:** Private identity keys and local database message payloads are encrypted at rest using an AES-256-GCM master key protected by the Android KeyStore (`AndroidKeyStore`). Fails closed on cipher errors (never returning plaintext or unverified data).
6. **Encrypted Local Storage:** Room database persists messages with `encryptedLocalPayload` encrypted via `KeyStoreMaster`. SQLite plaintext message columns are strictly excluded.
7. **Password Protection:** PBKDF2WithHmacSHA256 (10,000 iterations, 16-byte cryptographically secure random salt) for credential hashing.
8. **Safety Numbers & Key Rotation Detection:** Derived deterministically from SHA-512 hashes of sorted conversation identity keys, formatted as 12 5-digit numeric blocks (60 digits total). If a peer rotates identity keys, FORT alerts the user and requires re-verification.

### 2.2 Server Relay Architecture
The server relay (`FortRemoteBackend` / Cloud Firestore) functions strictly as a zero-knowledge transport:
* The server stores and relays **only ciphertext, IV, ephemeral public keys, and digital signatures**.
* Plaintext message text is decrypted **exclusively on authorized client devices**.
* Server-side authorization rules enforce blocklists and pass validity before relaying packets.
* Pass claims are atomically enforced to guarantee single-use semantics.

---

## 3. Real Feature Implementations

| Feature | Production Implementation |
| :--- | :--- |
| **Real Accounts & Persistence** | SQLite Room Database (`FortDatabase`, v2 with `MIGRATION_1_2`) with entities for `UserAccount`, `PersonaCard`, `PeerConnection`, `ChatMessage`, `KnockFirstRequest`, `ContactPass`, and `MoodRing`. Persists across app restarts and process kills without synthetic mock profiles. |
| **Authentication Choices** | Clean onboarding supporting Email/Password, 6-digit Phone OTP verification, Google Sign-In, and self-service Password Reset recovery. |
| **Zero-Correlation Cards** | 4 distinct facets (Personal, Work, Travel, Marketplace) with isolated NIST P-256 ECDH public keys. Senders never discover whether the counterparty has other cards. |
| **Contact Passes & QR Engine** | Generates real scannable 2D matrix QR codes (`com.google.zxing:core:3.5.3`). Supports atomic single-use tokens, duration boundaries, and deterministic revocation enforced by server authorization. |
| **Knock First Sandbox** | Real queue holding untrusted incoming requests. Senders cannot call or trigger downloads. Triage actions (`Accept Once`, `Grant 7-Day`, `Decline`, `Block & Report`) update persistent connections and server blocklists. |
| **Modern Conversation Flow** | In-conversation text search, quoted replies with preview banner, real-time emoji message reactions, message editing with `(edited)` indicator, message deletion ("This message was deleted"), delivery receipts (`PENDING` clock, `SENT` single tick, `DELIVERED` double tick, `READ` blue double tick), and multi-type attachment picker. |
| **Offline Queue & Retry** | Outbound messages dispatched while offline are persisted in SQLite with `PENDING` status. Automatic outbox flush upon network reconnection and manual retry banner in conversation view. |
| **Circles & Rooms Administration** | Authenticated memberships, role-based controls (`ADMIN` / `MEMBER`), member invite dialogs, member removal/kick authorization, room exit, and task checklists. |
| **Mood Ring & Quiet Presence** | Manual emotion selection (`ANGRY`, `HAPPY`, `SAD`, `STRESSED`, `READY_TO_TALK`, `NEED_QUIET`) and actionable "What I Need" layers. Real audience boundaries (`PRIVATE`, `CONNECTIONS`, `SELECTED_PEOPLE`, `CIRCLES`) and decay timers (30 min, 2h, end of day, custom). Expired moods vanish automatically upon query. |
| **Privacy Check & Access Map** | Real session audit showing verified cryptographic fingerprints, safety numbers, hardware biometric lock via `androidx.biometric.BiometricPrompt`, and privacy toggles for online presence and typing indicators. |
| **Share Check (Media Scrubber)** | Local inspection via `androidx.exifinterface.media.ExifInterface` detecting GPS coordinates, camera hardware telemetry, and sensitive text patterns. Generates a sanitized copy with verified zero residual EXIF tags while preserving the original file. |

---

## 4. Verification & Automated Test Suite

The project includes an exhaustive automated test suite validating cryptographic correctness, sender signature verification, tampering detection, atomic pass claiming, offline outbox retry, Room schema migrations, and authorization invariants.

### Running Tests
Ensure `JAVA_HOME` points to your JDK / JBR (Java 17+ or Android Studio JBR):
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
.\gradlew.bat testDebugUnitTest --no-daemon
```

### Verified Test Cases (22 Tests, 100% Pass Rate)

#### Cryptographic & Security Verification:
* `testEndToEndEncryptionBetweenAliceAndBob`: Alice encrypts, server stores only ciphertext (zero readable text), Bob decrypts on-device, unauthorized attacker Charlie fails.
* `testSenderAuthenticationAndTamperDetection`: Senders sign packets using ECDSA; recipient validates authenticity. Tampered ciphertext or forged sender signatures are rejected with `SecurityException`.
* `testTwoAccountRegistrationAndZeroCorrelationPersonas`: Two accounts with 4 isolated personas possessing distinct NIST P-256 ECDH public keys.
* `testSafetyNumberComputationAndKeyChangeAlert`: Derives identical 60-digit safety numbers (12 blocks of 5 digits); detects peer key rotation and alters safety number.
* `testContactPassSingleUseAndRevocationEnforcement`: Single-use pass claimed by Bob; subsequent claim rejected. Alice revokes pass; subsequent claim rejected.
* `testAtomicSingleUsePassClaimConcurrently`: Concurrent multi-threaded pass claims are serialized atomically; exactly one claimant succeeds, second claimant is rejected.
* `testServerSideBlocklistRejectsInboundMessages`: Server-side blocklist rejects unauthorized transmission attempts.

#### Messaging, Persistence & Room Management:
* `testMessageReactionsEditAndDelete`: Real-time emoji reaction toggle, message edit state updates, and message deletion tombstones.
* `testOfflineMessageQueueingAndRetryOutbox`: Messages sent while offline enter `PENDING` outbox and succeed when retried upon connectivity.
* `testRoomCreationInviteAndAdminAuthorization`: Room creator invites members with role assignment; unauthorized non-admins cannot kick members; members can leave room.
* `testPhoneOtpAndGoogleAccountFlows`: 6-digit phone OTP dispatch/verification, Google sign-in session initialization, and password reset dispatch.
* `testPersistenceAcrossAppRestart`: Room database persistence across simulated app restart and process recreation.
* `testMoodRingAudienceIsolationAndRealTimeDecay`: Circles audience gating; expired moods vanish automatically without leaving historical records.
* `testShareCheckScrubberInspectsAndSanitizesMediaFile`: Media inspection flags EXIF GPS, telemetry, phone patterns; creates clean copy with verified 0 residual EXIF tags while preserving original.
* `testZXingQrCodeMatrixEncodingAndDecoding`: Real ZXing QR bitmap generation with JSON payload.

#### Architectural Integrity (`FortArchitectureTest`):
* `testRevocationDisclosure`: Verifies mandatory non-deceptive disclosures regarding pass revocation boundaries.
* `testShareCheckDisclosure`: Verifies automated scrubber disclosures.
* `testAllCardTypesRepresented`: Ensures all 4 zero-correlation card types are defined.
* `testAllPassDurationsRepresented`: Ensures all pass duration types are supported.
* `testAllKnockTriageActionsRepresented`: Ensures all triage options (`ACCEPT_ONCE`, `ACCEPT_7_DAYS`, `DECLINE`, `BLOCK_AND_REPORT`) are present.
* `testAllMoodTypesRepresented`: Ensures all 6 emotional states are present.
* `testAllShareCheckRiskLevelsRepresented`: Ensures all risk severity levels are enforced.

**Result:** `22 tests completed, 0 failures. BUILD SUCCESSFUL.`

---

## 5. Building the Debug APK

To compile the Android package:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
.\gradlew.bat assembleDebug --no-daemon
```
The output APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 6. External Backend Deployment & Credentials Setup

FORT includes a factory (`FortBackendFactory`) that initializes real production backends by default and provides full instructions if configuration files are missing:

### 6.1 Cloud Firestore / Firebase Setup
1. Create a Firebase project in the [Firebase Console](https://console.firebase.google.com/).
2. Enable **Cloud Firestore** and the Authentication providers you intend to use: **Email/Password**, **Phone**, and **Google**.
3. Register your Android app with package name `com.fort.messenger`. Add the debug/release SHA fingerprints requested by Firebase for Phone and Google sign-in.
4. Download the real `google-services.json` for this app and place it at `app/google-services.json`. The checked-in `app/google-services.json.example` is intentionally fake and cannot connect to Firebase. The Google Services Gradle plugin is applied only when the real file exists, so source-only CI builds still work.
5. Deploy the rules in [`firestore.rules`](https://github.com/istihadahmed007/fort_chat/blob/main/firestore.rules) to the same Firebase project:
   ```bash
   firebase deploy --only firestore:rules
   ```

The app now uses Firebase Authentication and Cloud Firestore for production accounts and messaging. Without a valid `google-services.json`, cloud authentication and messaging report that setup is missing; they do not create demo accounts or show fake success. CI can compile and run local tests without your Firebase project, but it cannot verify live sign-in or cloud access.

### 6.2 Security Rules Overview (`firestore.rules`)
* **Messages (`/messages/{messageId}`):** Only designated recipient or sender can read ciphertext packets. Senders cannot transmit to recipients where they are blocked. Recipient delivery/read receipts, participant reaction maps, and sender edits/deletions are strictly governed.
* **Contact Passes (`/contact_passes/{passId}`):** Single-use passes cannot be claimed twice (`isClaimed == false`). Revoked or expired passes cannot be claimed. Atomic transactions serialize claims.
* **Rooms (`/rooms/{roomId}`):** Only room creator or designated admins can modify members or update room configurations; ordinary members can update tasks or leave.
* **Moods (`/moods/{userId}`):** Only authorized audience (`PRIVATE`, `CONNECTIONS`, `SELECTED_PEOPLE`, `CIRCLES`) can query a peer's mood. Expired moods are rejected.
* **Blocklists (`/users/{userId}/blocklist/{blockedUserId}`):** Users can manage their blocklist; authenticated peers can query their own block status.

### 6.3 Real-Time Calling & WebRTC Signaling Notice
* Knock First protects against unsolicited peer-to-peer calling connections.
* To activate live WebRTC audio/video calling in production, an external WebRTC signaling service and STUN/TURN credentials (e.g. Coturn or Twilio Network Traversal) must be configured in `WebRtcEngine.kt`. The app UI clearly displays calling availability rather than simulating a fake call.

---

## 7. Security and Honesty Disclosures

* **No "100% Unhackable" Guarantee:** Security is bounded by device physical security, OS integrity, and cryptographic implementation correctness.
* **Deterministic Revocation:** Revoking a contact pass terminates future inbound transmissions through that token. It cannot remotely erase messages or media already stored on the recipient's physical device.
* **Share Check Disclosure:** Automated metadata checks assist privacy but cannot guarantee detection of all sensitive content (e.g. text rendered visually inside an image). Users are prompted to verify before dispatching.
* **Zero Telemetry Plaintext:** Private keys, passwords, and decrypted message text are never logged to Logcat or transmitted to analytics services.
* **Fail-Closed KeyStore Master Key:** In the event of hardware keystore corruption or invalid key state, cryptographic operations immediately throw `SecurityException` rather than falling back to unencrypted storage or plaintext transmission.
