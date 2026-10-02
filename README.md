# FORT — Sovereign Identity, Bounded Access & Private Messaging

> *"Your identity. Your circle. Your rules."*

**Platform:** Native Android (Kotlin / Jetpack Compose)  
**Design System:** `Sovereign Sanctuary` (Light / Dark High-Contrast Theme)  
**Architecture:** MVI / Clean State Management with Coroutines StateFlow

---

## Overview

FORT is a human-centered, native Android private messaging application engineered to eradicate the structural privacy and social failures of legacy telephone-number-based messengers:
* **Context Collapse:** Solved via **Zero-Correlation Connection Cards** (Personal, Work, Travel, Marketplace).
* **Persistent Indefinite Exposure:** Solved via **Bounded Contact Passes** (`One Conversation`, `7 Days`, `Custom Duration`, `Ongoing`) with deterministic revocation.
* **Knock First Sandbox:** Inbound contacts cannot call, send voice notes, or trigger media downloads prior to verification. External preview links are quarantined as plain-text without OpenGraph crawlers.
* **Emotional Availability Misalignment:** Solved via the **Mood Ring & Quiet Presence** micro-badge (20×20dp) with "What I Need" actionable layer, circle-gated audience isolation, and automatic time-based decay.
* **Honest Privacy Architecture:** Transparent, calm disclosures without deceptive "100% Unhackable" marketing claims.

---

## Key Modules & Specifications

### 1. Zero-Correlation Identity (Connection Cards)
* Fully isolated public key signatures, avatars, handles, and bios per facet.
* Strict zero-correlation: Counterparties never discover whether the user maintains other cards.

### 2. Contact Passes & Knock First Protocol
* **Pass Durations:**
  - `One Conversation`: Bounded session that closes upon completion.
  - `7 Days`: Ephemeral pass for brief engagements or projects.
  - `Custom Duration`: Epoch-based boundary.
  - `Ongoing`: Open access until revoked in the Access Map.
* **Deterministic Revocation:** Immediate termination of future inbound transmissions.
* **Mandatory Disclosure:**
  > *"Revoking access prevents future messages through this pass. It does not remotely delete messages or media already stored on the recipient's device."*

### 3. Mood Ring & Quiet Presence
* 20×20dp micro-badge anchored to avatar lower-right perimeter without obscuring avatar focus.
* **Emotions:** 😠 Angry, 😊 Happy, 😔 Sad, 😣 Stressed, 💬 Ready to talk, 🌙 Need quiet.
* **"What I Need":** `Listen to me` | `Distract me` | `Offer advice` | `Give me space`.
* **One-Tap Private Support:** Counterparties can tap to send *"I'm here 🤍"* without public vanity metrics.
* **Audience Isolation & Temporal Decay:** Visible only to designated Sharing Circles (e.g. *Close Circle*) with auto-decay (30 min, 2 hrs, End of Day, Custom).

### 4. Circles vs. Private Rooms
* **Sharing Circles:** Cryptographic visibility permission containers governing Mood Ring and availability (not broadcast group chats).
* **Private Rooms:** Ephemeral, lifecycle-bound group spaces (e.g., *Weekend Mountain Trek*, *Apartment Sublet*) with shared task checklists and auto-expiry countdowns.

### 5. Access Map Audit & Hardware Enclave
* Central audit table of all active contact passes, connected enclave devices (Hardware Keystore Level 3 StrongBox / Titan M2), and encrypted client-side backup vault (XChaCha20-Poly1305).
* Biometric app lock and notification preview redaction.
* Seamless English and Bengali (`বাংলা`) typography and language toggle.

### 6. Share Check (Pre-Flight Media Scrubber)
* On-device metadata sanitizer flagging EXIF GPS coordinates, phone numbers, and addresses.
* One-tap redaction before media payload leaves the enclave.

---

## Project Structure

```
c:/Users/Tonmoy/Documents/Fort/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/fort/messenger/
│       │   │   ├── FortApp.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── model/
│       │   │   │   ├── ConnectionCard.kt
│       │   │   │   ├── MoodRing.kt
│       │   │   │   ├── ContactPass.kt
│       │   │   │   ├── CircleAndRoom.kt
│       │   │   │   ├── KnockFirstRequest.kt
│       │   │   │   ├── ChatConversation.kt
│       │   │   │   ├── AccessMapAudit.kt
│       │   │   │   └── ShareCheckItem.kt
│       │   │   ├── viewmodel/
│       │   │   │   └── FortMainViewModel.kt
│       │   │   ├── ui/
│       │   │   │   ├── theme/ (Color, Type, Theme)
│       │   │   │   ├── components/ (MoodRingBadge, SovereignCard, SovereignTopBar, FilterChipBar, PassCountdownChip, ConnectionCardBadge, QuietPresenceBanner)
│       │   │   │   ├── navigation/ (FortDestination, FortBottomBar, FortNavGraph)
│       │   │   │   ├── screens/
│       │   │   │   │   ├── chats/ChatsHomeScreen.kt
│       │   │   │   │   ├── circles/CirclesRoomsScreen.kt
│       │   │   │   │   ├── requests/KnockFirstRequestsScreen.kt
│       │   │   │   │   ├── you/YouAccessMapScreen.kt
│       │   │   │   │   └── chat/ConversationScreen.kt
│       │   │   │   └── modals/
│       │   │   │       ├── MoodRingBottomSheet.kt
│       │   │   │       ├── PrivacyCheckDialog.kt
│       │   │   │       ├── ContactPassGeneratorSheet.kt
│       │   │   │       └── ShareCheckModal.kt
│       │   └── res/
│       └── test/
│           └── java/com/fort/messenger/
│               └── FortArchitectureTest.kt
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── local.properties
```
