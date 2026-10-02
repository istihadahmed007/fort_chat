# FORT — Sovereign Identity, Bounded Access & Private Messaging

> *"Your identity. Your circle. Your rules."*

**Platform:** Native Android (Kotlin / Jetpack Compose)  
**Design System:** `Sovereign Sanctuary` (Light / Dark High-Contrast Theme)  
**Architecture:** MVI / Clean Architecture with Coroutines StateFlow & SQLite Room Database  
**Cryptography:** NIST P-256 ECDH Key Agreement, HKDF-SHA256, AES-256-GCM Authenticated Encryption, Android Keystore Hardware Master Key

---

## 1. Overview & Sovereign Sanctuary Philosophy

FORT is a native Android private messaging application engineered to eradicate the structural privacy and social failures of legacy telephone-number-based messengers:
* **Context Collapse:** Eliminated via **Zero-Correlation Connection Cards** (Personal, Work, Travel, Marketplace), where each facet possesses an isolated cryptographic key pair, avatar, handle, and bio.
* **Persistent Indefinite Exposure:** Replaced by **Bounded Contact Passes** (`One Conversation`, `7 Days`, `Custom Duration`, `Ongoing`) featuring single-use enforcement and deterministic server-side revocation.
* **Knock First Protocol:** Quarantines inbound unverified communications. Senders cannot call, transmit voice notes, or trigger media downloads prior to verification. External links are rendered as plain-text without background crawler leaks.
* **Emotional Availability Misalignment:** Solved by the **Mood Ring & Quiet Presence** micro-badge (20×20dp) with manual emotion selection (including Angry, Stressed, and Need Quiet), circle-gated audience boundaries, and automatic time-based decay.
* **Honest Privacy Architecture:** Transparent disclosures without deceptive "100% Unhackable" marketing claims. No fabricated fingerprints, simulated device counts, or fake backup timestamps.

---

## 2. Security Architecture & Genuine End-to-End Encryption

### 2.1 Cryptographic Suite
FORT uses maintained, established industry cryptography:
1. **Key Agreement:** Elliptic Curve Diffie-Hellman (ECDH) over the NIST P-256 (`secp256r1`) curve.
2. **Key Derivation:** HKDF-SHA256 derives 256-bit symmetric encryption keys with salt and info binding.
3. **Authenticated Cipher:** AES-256-GCM with a unique 96-bit Initialization Vector (IV) generated per packet via `SecureRandom`.
4. **Safety Numbers:** Derived deterministically from SHA-512 hashes of sorted conversation identity keys, formatted as 12 5-digit numeric blocks (60 digits total). Peers can visually or cryptographically verify keys.
5. **Key Rotation Detection:** Peer public keys are recorded with connection sessions. If a peer changes or rotates identity keys, FORT flags the connection, alerts the user, and requires re-verification.
6. **Hardware Enclave Storage:** Private identity keys are encrypted using an AES-256-GCM master key protected by the Android KeyStore (`AndroidKeyStore`).

### 2.2 Server Relay Architecture
The server relay (`FortRemoteBackend` / Cloud Firestore) functions strictly as a zero-knowledge transport:
* The server stores and relays **only ciphertext, IV, and ephemeral public keys**.
* Plaintext message text is decrypted **exclusively on authorized client devices**.
* Server-side authorization rules enforce blocklists and pass validity before relaying packets.

---

## 3. Real Feature Implementations

| Feature | Production Implementation |
| :--- | :--- |
| **Real Accounts & Persistence** | SQLite Room Database (`FortDatabase`) with entities for `UserAccount`, `PersonaCard`, `PeerConnection`, `ChatMessage`, `KnockFirstRequest`, `ContactPass`, and `MoodRing`. Data persists across app restarts and process kills. |
| **Zero-Correlation Cards** | 4 distinct facets (Personal, Work, Travel, Marketplace) with isolated NIST P-256 ECDH public keys. Senders never discover whether the counterparty has other cards. |
| **Contact Passes & QR Engine** | Generates real scannable 2D matrix QR codes (`com.google.zxing:core:3.5.3`). Supports single-use tokens, duration boundaries, and deterministic revocation enforced by server authorization. |
| **Knock First Sandbox** | Real queue holding untrusted incoming requests. Senders cannot call or trigger downloads. Triage actions (`Accept Once`, `Grant 7-Day`, `Decline`, `Block & Report`) update persistent connections and server blocklists. |
| **Mood Ring & Quiet Presence** | Manual emotion selection (`ANGRY`, `HAPPY`, `SAD`, `STRESSED`, `READY_TO_TALK`, `NEED_QUIET`) and actionable "What I Need" layers. Real audience boundaries (`PRIVATE`, `CONNECTIONS`, `SELECTED_PEOPLE`, `CIRCLES`) and decay timers (30 min, 2h, end of day, custom). Expired moods vanish automatically upon query. |
| **Private Rooms & Circles** | Authenticated memberships, task checklists, and server-side access checks. |
| **Privacy Check & Access Map** | Real session audit showing verified cryptographic fingerprints, safety numbers, and hardware biometric lock via `androidx.biometric.BiometricPrompt`. |
| **Share Check (Media Scrubber)** | Local inspection via `androidx.exifinterface.media.ExifInterface` detecting GPS coordinates, camera hardware telemetry, and sensitive text patterns. Generates a sanitized copy with verified zero residual EXIF tags while preserving the original file. |

---

## 4. Verification & Automated Test Suite

The project includes thorough automated test suites validating cryptographic correctness, database persistence, and authorization invariants.

### Running Tests
Ensure `JAVA_HOME` points to your JDK / JBR (Java 17+ or Android Studio JBR):
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
.\gradlew.bat testDebugUnitTest --no-daemon
```

### Verified Test Cases
* `testTwoAccountRegistrationAndZeroCorrelationPersonas`: Two accounts with 4 isolated personas possessing distinct NIST P-256 ECDH public keys.
* `testEndToEndEncryptionBetweenAliceAndBob`: Alice encrypts, server stores only ciphertext (no readable text), Bob decrypts on-device, unauthorized attacker Charlie fails.
* `testSafetyNumberComputationAndKeyChangeAlert`: Derives identical 60-digit safety numbers (12 blocks of 5 digits); detects peer key rotation and alters safety number.
* `testContactPassSingleUseAndRevocationEnforcement`: Single-use pass claimed by Bob, subsequent claim by Charlie rejected by server authorization. Alice revokes pass; subsequent claim rejected.
* `testServerSideBlocklistRejectsInboundMessages`: Server-side blocklist rejects unauthorized transmission attempts.
* `testMoodRingAudienceIsolationAndRealTimeDecay`: Circles audience gating; expired moods vanish automatically without leaving historical records.
* `testShareCheckScrubberInspectsAndSanitizesMediaFile`: Media inspection flags EXIF GPS, telemetry, phone patterns; creates clean copy with verified 0 residual EXIF tags while preserving original.
* `testZXingQrCodeMatrixEncodingAndDecoding`: Real ZXing QR bitmap generation with JSON payload.
* `testPersistenceAcrossAppRestart`: Room database persistence across simulated app restart.
* `FortArchitectureTest`: Mandatory disclosures (`REVOCATION_DISCLOSURE`, `SHARE_CHECK_DISCLOSURE`) and enum invariants.

**Result:** `16 tests completed, 0 failures. BUILD SUCCESSFUL.`

---

## 5. Building the Debug APK

To compile the Android package:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
.\gradlew.bat assembleDebug --no-daemon
```
The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 6. External Backend Deployment & Credentials Setup

FORT includes an in-memory server relay (`InMemoryRemoteRelay`) for local development, testing, and offline operations. To deploy against live cloud infrastructure:

### 6.1 Cloud Firestore / Firebase Setup
1. Create a Firebase project in the [Firebase Console](https://console.firebase.google.com/).
2. Enable **Cloud Firestore** and **Firebase Authentication** (Email/Password).
3. Register your Android app with package name `com.fort.messenger`.
4. Download your `google-services.json` and place it in the `app/` directory (see `app/google-services.json.example` for the format).
5. Apply the server-side security rules provided in [`firestore.rules`](file:///firestore.rules):
   ```bash
   firebase deploy --only firestore:rules
   ```

### 6.2 Security Rules Overview (`firestore.rules`)
* **Packets (`/packets/{packetId}`):** Only the designated recipient can read ciphertext packets. Senders cannot read packets after transmission. Senders listed on the recipient's blocklist are rejected on the server.
* **Passes (`/passes/{passId}`):** Public read for unclaimed tokens. Single-use passes cannot be claimed twice. Revoked passes cannot be claimed.
* **Moods (`/moods/{userId}`):** Only users in `allowedAudienceIds` can query a peer's mood. Expired moods are rejected.
* **Blocklists (`/blocklists/{userId}/blocked/{peerId}`):** Users can only manage their own blocklists.

---

## 7. Security and Honesty Disclosures

* **No "100% Unhackable" Guarantee:** Security is bounded by device physical security, OS integrity, and cryptographic implementation correctness.
* **Deterministic Revocation:** Revoking a contact pass terminates future inbound transmissions through that token. It cannot remotely erase messages or media already stored on the recipient's physical device.
* **Share Check Disclosure:** Automated metadata checks assist privacy but cannot guarantee detection of all sensitive content (e.g. text rendered visually inside an image). Users are prompted to verify before dispatching.
* **Zero Telemetry Plaintext:** Private keys, passwords, and decrypted message text are never logged to logcat or transmitted to analytics services.
