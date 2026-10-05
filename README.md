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
| **Authentication Choices** | Clean onboarding supporting Email/Password, 6-digit Phone OTP verification, Google Sign-In, and self-service Password Reset recovery. Branded with the 3D Fortress Shield icon. |
| **Zero-Correlation Cards** | 4 distinct facets (Personal, Work, Travel, Marketplace) with isolated NIST P-256 ECDH public keys. Senders never discover whether the counterparty has other cards. |
| **Portable Identity Backup** | Export a passphrase-encrypted identity backup from Identity & Access Map and restore the same keys after signing in on a new device. A new-device sign-in never silently rotates existing keys; key reset requires explicit confirmation and warns that older messages will no longer be decryptable. |
| **Camera QR Scanner & Pass Engine** | Live CameraX viewfinder (`PassScannerModal`) analyzing real-time camera frames with ZXing (`com.google.zxing:core:3.5.3`). Features manual token entry fallback, graceful camera permission denial handling, invalid/expired/revoked token handling, and offline error states. Generates scannable QR bitmaps. |
| **Preview Before Connect** | Scanned invitations trigger a `PassClaimPreviewDialog` showing the inviter's persona card, access duration, and expiration timestamp. Connection requires explicit user confirmation via "Accept & Connect" or "Cancel". |
| **Reciprocal Two-Way Connection** | Upon pass claim, claimant saves the peer connection and immediately transmits an authenticated, end-to-end encrypted sovereign handshake packet. When the issuer syncs inbound messages, a reciprocal connection is automatically established in their local database, placing the new conversation in both users' Chats screen. |
| **Fail-Closed Pass Publishing** | If remote publishing fails (network failure, Firestore permission denial, or offline), the invitation is rejected immediately and is never saved to the local Room database as active or usable. |
| **New Chat Actions & Empty State** | Chats screen features a prominent "New Chat" action sheet and empty state quick-action buttons offering "Scan QR Invitation", "Create Invitation Pass", and "Find by Fort ID". Top-bar QR action is wired directly to the live Camera scanner. |
| **User Discovery & Privacy** | Multi-mode discovery supporting exact Fort ID matching, display name matching with duplicate-name collision prevention, and rate-limited phone number search requiring verified requester credentials to stop scraper enumeration. |
| **Knock First Protocol & Reciprocal Handshake** | Quarantines unverified inbound communications. Senders cannot call or trigger downloads. Triage actions (`Accept Once`, `Grant 7-Day`, `Decline`, `Block & Report`) transmit reciprocal encrypted handshakes to automatically establish two-way channels. |
| **WebRTC Audio & Video Calling** | Authenticated signaling state machine (`RINGING`, `ACCEPTED`, `REJECTED`, `ENDED`) and ICE candidate exchange for 1-to-1 audio and video calls. |
| **Private Location Sharing** | End-to-end encrypted static location pins and duration-limited ephemeral live location sharing (15 min, 1h, 8h) with real-time coordinate updates and automatic expiration cleanup. |
| **Modern Conversation Flow** | In-conversation text search, quoted replies with preview banner, real-time emoji message reactions, draft retention across chats, live typing indicators, delivery/read receipts (`PENDING` clock, `SENT` single tick, `DELIVERED` double tick, `READ` emerald double tick), message editing with `(edited)` indicator, message deletion tombstones, and multi-type attachments. |
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

### Verified Test Cases (39 Tests, 100% Pass Rate)

#### Cryptographic & Security Verification:
* `testEndToEndEncryptionBetweenAliceAndBob`: Alice encrypts, server stores only ciphertext (zero readable text), Bob decrypts on-device, unauthorized attacker Charlie fails.
* `testSenderAuthenticationAndTamperDetection`: Senders sign packets using ECDSA; recipient validates authenticity. Tampered ciphertext or forged sender signatures are rejected with `SecurityException`.
* `testTwoAccountRegistrationAndZeroCorrelationPersonas`: Two accounts with 4 isolated personas possessing distinct NIST P-256 ECDH public keys.
* `testSafetyNumberComputationAndKeyChangeAlert`: Derives identical 60-digit safety numbers (12 blocks of 5 digits); detects peer key rotation and alters safety number.
* `testContactPassSingleUseAndRevocationEnforcement`: Single-use pass claimed by Bob; subsequent claim rejected. Alice revokes pass; subsequent claim rejected.
* `testAtomicSingleUsePassClaimConcurrently`: Concurrent multi-threaded pass claims are serialized atomically; exactly one claimant succeeds, second claimant is rejected.
* `testServerSideBlocklistRejectsInboundMessages`: Server-side blocklist rejects unauthorized transmission attempts.
* `testQrBitmapGenerationAndDecoding`: Real ZXing QR bitmap generation, 2D matrix rendering, and camera frame decoding via `QRCodeReader`.
* `testTwoWayConnectionCreationAndMessaging`: Bob claims Alice's pass, transmits an E2EE greeting handshake; Alice syncs inbound messages and automatically creates the reciprocal peer connection, enabling two-way conversation.
* `testPassPublishFailureDoesNotSaveLocalPass`: Remote publishing failure (e.g. offline/network failure) fails closed and does not store un-published passes in local SQLite storage.
* `testQrInvitationClaimSuccessAndFailureScenarios`: Pass claiming with passId fallback, single-use duplication rejection, normalized token variants (without `PASS-` prefix, lowercase), and revoked pass rejection.

#### Discovery, Calling & Location Sharing:
* `testPeopleDiscoveryAndPrivacyRestrictions`: Multi-mode user discovery (display name duplicate handling, Fort ID search, and anti-enumeration verified phone search).
* `testKnockFirstReciprocalConnectionAndConversationInitialization`: Knock First flow from search discovery, inbound sync, 7-day connection grant, and automated reciprocal handshake.
* `testWebRtcSignalingAudioVideoCallStates`: Outgoing/incoming audio/video calling states (`RINGING` -> `ACCEPTED` -> `ENDED`) and authenticated ICE candidate exchange.
* `testEphemeralLocationSharingPinLiveAndExpiry`: Static map pin encryption, 15-minute live location session publishing, coordinate updates, and stop sharing with expiration verification.
* `testPresenceAndTypingIndicators`: Real-time typing status dispatch and peer typing observation flows.

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

**Result:** `39 tests completed, 0 failures. BUILD SUCCESSFUL.`

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
5. Configure a production Coturn service and the server-only variables shown in [`functions/.env.example`](functions/.env.example). Keep the Functions `.env` file out of version control.
6. Deploy the Firestore rules and callable functions:
   ```bash
   firebase deploy --only firestore:rules,functions
   ```

The app now uses Firebase Authentication and Cloud Firestore for production accounts and messaging. Without a valid `google-services.json`, cloud authentication and messaging report that setup is missing; they do not create demo accounts or show fake success. CI can compile and run local tests without your Firebase project, but it cannot verify live sign-in or cloud access.

### 6.2 Security Rules Overview (`firestore.rules`)
* **Private Accounts (`/users/{userId}`):** Strictly owner-only (`request.auth.uid == userId`). Prohibits broad authenticated reads of full account profiles to prevent exposing sensitive email addresses, raw phone numbers, and private session tokens.
* **Minimal Public Profiles (`/public_profiles/{userId}`):** Minimal discovery projection containing only safe display and discovery fields. Raw phone numbers and phone hashes are excluded; profile phone writes must match the signed Firebase Auth phone claim.
* **Unique Fort ID Registry (`/fort_ids/{cleanFortId}`):** Uniqueness reservation enforcing one owner per Fort ID.
* **Encrypted Message Packets (`/messages/{messageId}`):** Only designated recipient or sender can read ciphertext packets. Senders cannot transmit to recipients where they are blocked. Recipient delivery/read receipts, participant reaction maps, and sender edits/deletions are strictly governed.
* **Anti-Scraping Contact Passes (`/contact_passes/{passId}`):** Readable exclusively by the pass issuer and the verified claimant. Prohibits global collection reads or token scraping.
* **Secure Pass Token Claims (`/pass_tokens/{tokenHash}`):** Keyed by SHA-256 hash of the invitation token. Enforces atomic single-use claims, expiration validation, and deterministic revocation without exposing a scrapeable registry.
* **WebRTC Signaling & ICE Isolation (`/calls/{callId}`):** Strictly restricted to the caller and receiver of that specific call. Participants cannot alter `callerUserId` or `receiverUserId`. ICE candidates in `/calls/{callId}/candidates/{candidateId}` can only be read or written by the active call participants.
* **Rooms (`/rooms/{roomId}`):** Only room creator or designated admins can modify members or update room configurations; ordinary members can update tasks or leave.
* **Moods (`/moods/{userId}`):** Only authorized audience (`PRIVATE`, `CONNECTIONS`, `SELECTED_PEOPLE`, `CIRCLES`) can query a peer's mood. Expired moods are rejected.
* **Blocklists (`/users/{userId}/blocklist/{blockedUserId}`):** Users can manage their blocklist; authenticated peers can query their own block status.

### 6.3 Real-Time Calling & TURN Configuration
FORT uses WebRTC Unified Plan with public STUN fallbacks. Networks that block direct peer connections need a TURN relay.

Configure Coturn REST authentication and the same shared secret on the TURN server and Firebase Functions. Copy `functions/.env.example` to an untracked `functions/.env` and set:
- `FORT_TURN_HOSTS`: comma-separated `turn:` or `turns:` URLs, including ports.
- `FORT_TURN_SHARED_SECRET`: the Coturn REST `static-auth-secret`. This secret stays on the server and is never included in the Android app.
- `FORT_TURN_TTL_SECONDS`: credential lifetime (300–86400 seconds; defaults to 3600).

The authenticated `getTurnCredentials` callable generates per-user, expiring HMAC credentials. The Android app requests them when a call starts and adds them to its ICE servers. If TURN is not configured, calls use STUN only and may fail on restrictive networks. Deploy callable changes with `firebase deploy --only functions`; never commit a real `.env` file.

### 6.4 Firebase Emulator Rules Test Suite
The security rules and permission boundaries are verified against the real Firebase Firestore Emulator:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
npx firebase-tools emulators:exec --only firestore run_rules_test.bat
```
Validates 24 security assertions including unauthorized profile read blocking, pass token scraping prevention, call candidate snooping rejection, participant alteration denial, and cross-user spoofing rejection.

---

## 7. Security and Honesty Disclosures

* **No "100% Unhackable" Guarantee:** Security is bounded by device physical security, OS integrity, and cryptographic implementation correctness.
* **Deterministic Revocation:** Revoking a contact pass terminates future inbound transmissions through that token. It cannot remotely erase messages or media already stored on the recipient's physical device.
* **Share Check Disclosure:** Automated metadata checks assist privacy but cannot guarantee detection of all sensitive content (e.g. text rendered visually inside an image). Users are prompted to verify before dispatching.
* **Zero Telemetry Plaintext:** Private keys, passwords, and decrypted message text are never logged to Logcat or transmitted to analytics services.
* **Fail-Closed KeyStore Master Key:** In the event of hardware keystore corruption or invalid key state, cryptographic operations immediately throw `SecurityException` rather than falling back to unencrypted storage or plaintext transmission.
