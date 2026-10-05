const crypto = require("crypto");

const FIRESTORE_PORT = process.env.FIRESTORE_EMULATOR_PORT || 8080;
const FUNCTIONS_PORT = process.env.FUNCTIONS_EMULATOR_PORT || 5001;
const PROJECT_ID = process.env.GCLOUD_PROJECT || process.env.PROJECT_ID || "demo-fort-chat";

process.env.FIRESTORE_EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || `127.0.0.1:${FIRESTORE_PORT}`;
process.env.FIREBASE_AUTH_EMULATOR_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || "127.0.0.1:9099";
let admin;
try {
    admin = require("firebase-admin");
} catch (e1) {
    try {
        admin = require("./functions/node_modules/firebase-admin");
    } catch (e2) {
        admin = require("../functions/node_modules/firebase-admin");
    }
}
admin.initializeApp({ projectId: PROJECT_ID });
const adminAuth = admin.auth();
const adminDb = admin.firestore();

const BASE_URL = `http://127.0.0.1:${FIRESTORE_PORT}/v1/projects/${PROJECT_ID}/databases/(default)/documents`;
const FUNCTIONS_URL = `http://127.0.0.1:${FUNCTIONS_PORT}/${PROJECT_ID}/us-central1`;

function sha256Hex(str) {
    return crypto.createHash("sha256").update(str, "utf8").digest("hex");
}

function makeMockJwt(uid, customClaims = {}) {
    if (!uid) return null;
    const header = Buffer.from(JSON.stringify({ alg: "none", typ: "JWT" })).toString('base64url');
    const payload = Buffer.from(JSON.stringify({
        user_id: uid,
        sub: uid,
        aud: PROJECT_ID,
        iss: `https://securetoken.google.com/${PROJECT_ID}`,
        ...customClaims
    })).toString('base64url');
    return `${header}.${payload}.`;
}

function makeAuthHeader(uid, customClaims = {}) {
    if (!uid) return {};
    return { 'Authorization': `Bearer ${makeMockJwt(uid, customClaims)}` };
}

async function requestFirestore(path, method = 'GET', body = null, uid = null, customClaims = {}) {
    const url = `${BASE_URL}/${path}`;
    const headers = {
        'Content-Type': 'application/json',
        ...makeAuthHeader(uid, customClaims)
    };
    const options = { method, headers };
    if (body) {
        options.body = JSON.stringify(body);
    }
    const res = await fetch(url, options);
    const data = await res.json().catch(() => ({}));
    return { status: res.status, data };
}

async function callFunction(name, data = {}, uid = null, customClaims = {}) {
    const url = `${FUNCTIONS_URL}/${name}`;
    const headers = {
        'Content-Type': 'application/json',
        ...makeAuthHeader(uid, customClaims)
    };
    const res = await fetch(url, {
        method: 'POST',
        headers,
        body: JSON.stringify({ data })
    });
    const resBody = await res.json().catch(() => ({}));
    return { status: res.status, data: resBody };
}

// Convert JSON object to Firestore REST API fields structure
function toFirestoreFields(obj) {
    const fields = {};
    for (const [key, value] of Object.entries(obj)) {
        if (typeof value === 'string') {
            fields[key] = { stringValue: value };
        } else if (typeof value === 'boolean') {
            fields[key] = { booleanValue: value };
        } else if (typeof value === 'number') {
            if (Number.isInteger(value)) {
                fields[key] = { integerValue: value.toString() };
            } else {
                fields[key] = { doubleValue: value };
            }
        } else if (Array.isArray(value)) {
            fields[key] = { arrayValue: { values: value.map(v => ({ stringValue: v })) } };
        } else if (value === null) {
            fields[key] = { nullValue: null };
        }
    }
    return { fields };
}

let passedTests = 0;
let failedTests = 0;

function assert(condition, message) {
    if (condition) {
        console.log(`  ✅ PASS: ${message}`);
        passedTests++;
    } else {
        console.error(`  ❌ FAIL: ${message}`);
        failedTests++;
    }
}

async function runTests() {
    console.log("=================================================");
    console.log(`Starting Fort Firebase Emulator Rules & Functions Test Suite`);
    console.log(`Target: Firestore port ${FIRESTORE_PORT}, Functions port ${FUNCTIONS_PORT}, Project: ${PROJECT_ID}`);
    console.log("=================================================\n");

    const ALICE_UID = "alice_user_1";
    const BOB_UID = "bob_user_2";
    const CHARLIE_UID = "charlie_user_3";
    const DAVE_BLOCKED_UID = "dave_blocked_4";
    const EVE_UID = "eve_attacker_5";

    await Promise.all([
        adminAuth.createUser({ uid: ALICE_UID, phoneNumber: "+15551234567" }),
        adminAuth.createUser({ uid: BOB_UID, phoneNumber: "+15559876543" }),
        adminAuth.createUser({ uid: CHARLIE_UID, phoneNumber: "+15550001111" }),
        adminAuth.createUser({ uid: EVE_UID })
    ]);

    // -----------------------------------------------------------------
    // TEST 1: Private User Profiles vs Minimal Public Profiles & phoneHash Elimination
    // -----------------------------------------------------------------
    console.log("--- 1. Testing User Profile Privacy & Phone Hash Elimination ---");
    
    // Alice creates private profile
    const alicePrivateProfile = toFirestoreFields({
        userId: ALICE_UID,
        email: "alice@secret.vault",
        phoneNumber: "+15551234567",
        displayName: "Alice Sovereign",
        authToken: "tok_private_123"
    });
    const createPrivateRes = await requestFirestore(
        `users/${ALICE_UID}`, 'PATCH', alicePrivateProfile, ALICE_UID,
        { phone_number: "+15551234567" }
    );
    assert(createPrivateRes.status === 200, "Alice can create her own verified private /users document");

    const evePhoneSpoofRes = await requestFirestore(`users/${EVE_UID}`, 'PATCH', toFirestoreFields({
        userId: EVE_UID,
        phoneNumber: "+15559876543"
    }), EVE_UID);
    assert(evePhoneSpoofRes.status === 403, "A user cannot write an unverified phone number to their profile");

    // Alice creates her persona card with public key
    const aliceCard = toFirestoreFields({
        cardId: "card_alice_personal",
        userId: ALICE_UID,
        type: "PERSONAL",
        publicKey: "pub_alice_key_ecc_p256",
        displayName: "Alice"
    });
    const createCardRes = await requestFirestore(`users/${ALICE_UID}/cards/PERSONAL`, 'PATCH', aliceCard, ALICE_UID);
    assert(createCardRes.status === 200, "Alice can publish her personal card public key");

    // Alice creates minimal public profile (NO phoneHash)
    const alicePublicProfile = toFirestoreFields({
        userId: ALICE_UID,
        displayName: "Alice Sovereign",
        normalizedDisplayName: "alice sovereign",
        fortId: "@alice.fort",
        hasVerifiedPhone: true,
        discoverableByName: true,
        discoverableByPhone: true
    });
    const createPublicRes = await requestFirestore(`public_profiles/${ALICE_UID}`, 'PATCH', alicePublicProfile, ALICE_UID);
    assert(createPublicRes.status === 200, "Alice can create her minimal public profile");

    const alicePhoneSpoofUpdateRes = await requestFirestore(
        `users/${ALICE_UID}`, 'PATCH', toFirestoreFields({ phoneNumber: "+15559876543" }), ALICE_UID,
        { phone_number: "+15551234567" }
    );
    assert(alicePhoneSpoofUpdateRes.status === 403, "A user cannot change their profile phone to another number");

    const aliceUnrelatedProfileUpdateRes = await requestFirestore(
        `users/${ALICE_UID}`, 'PATCH', toFirestoreFields({ displayName: "Alice Updated" }), ALICE_UID,
        { phone_number: "+15551234567" }
    );
    assert(aliceUnrelatedProfileUpdateRes.status === 200, "Unrelated profile updates still work with the verified phone unchanged");

    // Attempting to publish unsalted phoneHash in public profile MUST BE REJECTED
    const publicProfileWithPhoneHash = toFirestoreFields({
        userId: ALICE_UID,
        displayName: "Alice Sovereign",
        phoneHash: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    });
    const tamperPhoneHashRes = await requestFirestore(`public_profiles/${ALICE_UID}`, 'PATCH', publicProfileWithPhoneHash, ALICE_UID);
    assert(tamperPhoneHashRes.status === 403, "Publishing phoneHash in /public_profiles is REJECTED by rules (403 Forbidden)");

    // Eve cannot read Alice's private profile
    const eveReadPrivateRes = await requestFirestore(`users/${ALICE_UID}`, 'GET', null, EVE_UID);
    assert(eveReadPrivateRes.status === 403, "Eve CANNOT read Alice's private /users document (403 Forbidden)");

    // Eve can read Alice's minimal public profile
    const eveReadPublicRes = await requestFirestore(`public_profiles/${ALICE_UID}`, 'GET', null, EVE_UID);
    assert(eveReadPublicRes.status === 200, "Eve CAN read Alice's minimal /public_profiles document");


    // -----------------------------------------------------------------
    // TEST 2: Atomic Fort ID Registry & Squatting Prevention
    // -----------------------------------------------------------------
    console.log("\n--- 2. Testing Fort ID Unique Reservation & Anti-Squatting ---");

    // Alice reserves @alice.fort
    const aliceIdDoc = toFirestoreFields({
        userId: ALICE_UID,
        fortId: "@alice.fort",
        reservedAt: Date.now()
    });
    const reserveAliceRes = await requestFirestore(`fort_ids/@alice.fort`, 'PATCH', aliceIdDoc, ALICE_UID);
    assert(reserveAliceRes.status === 200, "Alice can reserve @alice.fort matching her UID");

    // Eve attempts to squat @alice.fort (MUST BE REJECTED)
    const eveSquatAliceDoc = toFirestoreFields({
        userId: EVE_UID,
        fortId: "@alice.fort",
        reservedAt: Date.now()
    });
    const eveSquatRes = await requestFirestore(`fort_ids/@alice.fort`, 'PATCH', eveSquatAliceDoc, EVE_UID);
    assert(eveSquatRes.status === 403, "Eve CANNOT overwrite/squat Alice's reserved Fort ID (403 Forbidden)");

    // Eve attempts to create mismatched ID reservation (path != fortId field)
    const eveMismatchedDoc = toFirestoreFields({
        userId: EVE_UID,
        fortId: "@target_stolen.fort",
        reservedAt: Date.now()
    });
    const eveMismatchRes = await requestFirestore(`fort_ids/@eve.fort`, 'PATCH', eveMismatchedDoc, EVE_UID);
    assert(eveMismatchRes.status === 403, "Eve CANNOT reserve Fort ID with mismatched document path (403 Forbidden)");

    // Eve reserves her legitimate Fort ID
    const eveLegitDoc = toFirestoreFields({
        userId: EVE_UID,
        fortId: "@eve.fort",
        reservedAt: Date.now()
    });
    const eveLegitRes = await requestFirestore(`fort_ids/@eve.fort`, 'PATCH', eveLegitDoc, EVE_UID);
    assert(eveLegitRes.status === 200, "Eve can reserve @eve.fort matching path and UID");


    // -----------------------------------------------------------------
    // TEST 3: Contact Pass Direct Access Denial & Anti-Scraping Rules
    // -----------------------------------------------------------------
    console.log("\n--- 3. Testing Contact Pass & Token Direct Access Denials ---");

    const passId = "pass_test_secure_100";
    const validToken = "PASS-TEST-TOKEN-999";
    const validTokenHash = sha256Hex(validToken);
    const passExpiresAt = Date.now() + 86400000;

    // Alice creates contact pass
    const passDoc = toFirestoreFields({
        passId: passId,
        issuerUserId: ALICE_UID,
        cardType: "PERSONAL",
        durationType: "SEVEN_DAYS",
        token: validToken,
        isRevoked: false,
        isClaimed: false,
        isSingleUse: true,
        expiresAt: passExpiresAt,
        issuerPublicKey: "pub_alice_key_ecc_p256"
    });
    const createPassRes = await requestFirestore(`contact_passes/${passId}`, 'PATCH', passDoc, ALICE_UID);
    assert(createPassRes.status === 200, "Alice can create a contact pass");

    // Alice creates the token record in /pass_tokens/{tokenHash}
    const tokenDoc = toFirestoreFields({
        tokenHash: validTokenHash,
        passId: passId,
        issuerUserId: ALICE_UID,
        expiresAt: passExpiresAt,
        isRevoked: false,
        isClaimed: false
    });
    const createTokenRes = await requestFirestore(`pass_tokens/${validTokenHash}`, 'PATCH', tokenDoc, ALICE_UID);
    assert(createTokenRes.status === 200, "Alice can publish pass token in /pass_tokens/{tokenHash}");

    // Eve attempts to read Alice's unclaimed pass directly (MUST BE REJECTED)
    const eveReadPassRes = await requestFirestore(`contact_passes/${passId}`, 'GET', null, EVE_UID);
    assert(eveReadPassRes.status === 403, "Unclaimant CANNOT read unclaimed pass directly (403 Forbidden)");

    // Bob attempts to read /pass_tokens directly (MUST BE REJECTED - Anti-Enumeration/Anti-Scraping)
    const bobReadTokenDirectRes = await requestFirestore(`pass_tokens/${validTokenHash}`, 'GET', null, BOB_UID);
    assert(bobReadTokenDirectRes.status === 403, "Clients CANNOT directly read or list /pass_tokens (403 Forbidden)");

    // Bob attempts to directly write/claim /contact_passes (MUST BE REJECTED - Functions-only claim)
    const directClaimPassRes = await requestFirestore(`contact_passes/${passId}`, 'PATCH', toFirestoreFields({
        isClaimed: true,
        claimantUserId: BOB_UID
    }), BOB_UID);
    assert(directClaimPassRes.status === 403, "Clients CANNOT directly update/claim /contact_passes (403 Forbidden)");

    // Bob attempts to directly write/claim /pass_tokens (MUST BE REJECTED - Functions-only claim)
    const directClaimTokenRes = await requestFirestore(`pass_tokens/${validTokenHash}`, 'PATCH', toFirestoreFields({
        isClaimed: true,
        claimantUserId: BOB_UID
    }), BOB_UID);
    assert(directClaimTokenRes.status === 403, "Clients CANNOT directly update/claim /pass_tokens (403 Forbidden)");


    // -----------------------------------------------------------------
    // TEST 4: Trusted Callable Cloud Function: claimContactPass
    // -----------------------------------------------------------------
    console.log("\n--- 4. Testing Trusted Callable Cloud Function: claimContactPass ---");

    // 4.1 Unauthenticated caller fails
    const unauthClaimRes = await callFunction("claimContactPass", { token: validToken }, null);
    assert(
        unauthClaimRes.status === 401 || (unauthClaimRes.data && unauthClaimRes.data.error && unauthClaimRes.data.error.status === 'UNAUTHENTICATED'),
        "Unauthenticated claim call fails with UNAUTHENTICATED error"
    );

    // 4.2 Valid claim by Bob succeeds through callable function
    const bobClaimRes = await callFunction("claimContactPass", { token: validToken }, BOB_UID);
    const bobResult = bobClaimRes.data && bobClaimRes.data.result;
    assert(
        bobClaimRes.status === 200 && bobResult != null && bobResult.passId === passId && bobResult.issuerUserId === ALICE_UID,
        "Bob can successfully claim active pass via trusted callable function"
    );
    assert(
        bobResult != null && bobResult.issuerPublicKey === "pub_alice_key_ecc_p256" && bobResult.email === undefined && bobResult.phoneNumber === undefined,
        "claimContactPass returns ONLY minimal connection metadata; zero private account data exposed"
    );

    // 4.3 Second claim of single-use pass fails (already claimed)
    const secondClaimRes = await callFunction("claimContactPass", { token: validToken }, CHARLIE_UID);
    assert(
        secondClaimRes.status === 409 || (secondClaimRes.data && secondClaimRes.data.error && (secondClaimRes.data.error.status === 'ALREADY_EXISTS' || secondClaimRes.data.error.message.includes('already claimed'))),
        "Second claim of single-use pass fails with ALREADY_EXISTS"
    );

    // 4.4 Wrong/Non-existent token fails
    const wrongTokenRes = await callFunction("claimContactPass", { token: "PASS-NON-EXISTENT-XYZ" }, CHARLIE_UID);
    assert(
        wrongTokenRes.status === 404 || (wrongTokenRes.data && wrongTokenRes.data.error && (wrongTokenRes.data.error.status === 'NOT_FOUND' || wrongTokenRes.data.error.message.includes('not found'))),
        "Non-existent token claim fails with NOT_FOUND"
    );

    // 4.5 Expired pass fails
    const expiredPassId = "pass_expired_999";
    const expiredToken = "PASS-EXPIRED-TOKEN-000";
    const expiredTokenHash = sha256Hex(expiredToken);
    await requestFirestore(`contact_passes/${expiredPassId}`, 'PATCH', toFirestoreFields({
        passId: expiredPassId,
        issuerUserId: ALICE_UID,
        cardType: "PERSONAL",
        durationType: "ONE_CONVERSATION",
        token: expiredToken,
        isRevoked: false,
        isClaimed: false,
        isSingleUse: true,
        expiresAt: Date.now() - 5000, // Expired in past
        issuerPublicKey: "pub_alice_key_ecc_p256"
    }), ALICE_UID);
    await requestFirestore(`pass_tokens/${expiredTokenHash}`, 'PATCH', toFirestoreFields({
        tokenHash: expiredTokenHash,
        passId: expiredPassId,
        issuerUserId: ALICE_UID,
        expiresAt: Date.now() - 5000,
        isRevoked: false,
        isClaimed: false
    }), ALICE_UID);

    const claimExpiredRes = await callFunction("claimContactPass", { token: expiredToken }, CHARLIE_UID);
    assert(
        claimExpiredRes.status === 400 || (claimExpiredRes.data && claimExpiredRes.data.error && (claimExpiredRes.data.error.status === 'FAILED_PRECONDITION' || claimExpiredRes.data.error.message.includes('expired'))),
        "Expired pass claim fails with FAILED_PRECONDITION"
    );

    // 4.6 Revoked pass fails
    const revokedPassId = "pass_revoked_888";
    const revokedToken = "PASS-REVOKED-TOKEN-888";
    const revokedTokenHash = sha256Hex(revokedToken);
    // 1. Alice creates active pass and token
    await requestFirestore(`contact_passes/${revokedPassId}`, 'PATCH', toFirestoreFields({
        passId: revokedPassId,
        issuerUserId: ALICE_UID,
        cardType: "PERSONAL",
        durationType: "SEVEN_DAYS",
        token: revokedToken,
        isRevoked: false,
        isClaimed: false,
        isSingleUse: true,
        expiresAt: Date.now() + 86400000,
        issuerPublicKey: "pub_alice_key_ecc_p256"
    }), ALICE_UID);
    await requestFirestore(`pass_tokens/${revokedTokenHash}`, 'PATCH', toFirestoreFields({
        tokenHash: revokedTokenHash,
        passId: revokedPassId,
        issuerUserId: ALICE_UID,
        expiresAt: Date.now() + 86400000,
        isRevoked: false,
        isClaimed: false
    }), ALICE_UID);

    // 2. Alice revokes the pass (authorized update under firestore.rules)
    await requestFirestore(`contact_passes/${revokedPassId}?updateMask.fieldPaths=isRevoked`, 'PATCH', toFirestoreFields({
        isRevoked: true
    }), ALICE_UID);
    await requestFirestore(`pass_tokens/${revokedTokenHash}?updateMask.fieldPaths=isRevoked`, 'PATCH', toFirestoreFields({
        isRevoked: true
    }), ALICE_UID);

    const claimRevokedRes = await callFunction("claimContactPass", { token: revokedToken }, CHARLIE_UID);
    assert(
        claimRevokedRes.status === 400 || (claimRevokedRes.data && claimRevokedRes.data.error && (claimRevokedRes.data.error.status === 'FAILED_PRECONDITION' || claimRevokedRes.data.error.message.includes('revoked'))),
        "Revoked pass claim fails with FAILED_PRECONDITION"
    );

    // 4.7 Blocked claimant fails
    // Alice blocks Dave
    await requestFirestore(`users/${ALICE_UID}/blocklist/${DAVE_BLOCKED_UID}`, 'PATCH', toFirestoreFields({
        blockedUserId: DAVE_BLOCKED_UID,
        createdAt: Date.now()
    }), ALICE_UID);

    const blockedPassId = "pass_blocked_test_777";
    const blockedToken = "PASS-BLOCKED-TOKEN-777";
    const blockedTokenHash = sha256Hex(blockedToken);
    await requestFirestore(`contact_passes/${blockedPassId}`, 'PATCH', toFirestoreFields({
        passId: blockedPassId,
        issuerUserId: ALICE_UID,
        cardType: "PERSONAL",
        durationType: "SEVEN_DAYS",
        token: blockedToken,
        isRevoked: false,
        isClaimed: false,
        isSingleUse: true,
        expiresAt: Date.now() + 86400000,
        issuerPublicKey: "pub_alice_key_ecc_p256"
    }), ALICE_UID);
    await requestFirestore(`pass_tokens/${blockedTokenHash}`, 'PATCH', toFirestoreFields({
        tokenHash: blockedTokenHash,
        passId: blockedPassId,
        issuerUserId: ALICE_UID,
        expiresAt: Date.now() + 86400000,
        isRevoked: false,
        isClaimed: false
    }), ALICE_UID);

    const claimBlockedRes = await callFunction("claimContactPass", { token: blockedToken }, DAVE_BLOCKED_UID);
    assert(
        claimBlockedRes.status === 403 || (claimBlockedRes.data && claimBlockedRes.data.error && (claimBlockedRes.data.error.status === 'PERMISSION_DENIED' || claimBlockedRes.data.error.message.includes('cannot claim'))),
        "Blocked claimant cannot claim pass (PERMISSION_DENIED)"
    );


    // -----------------------------------------------------------------
    // TEST 5: Trusted Callable Cloud Function: lookupUserByPhone
    // -----------------------------------------------------------------
    console.log("\n--- 5. Testing Trusted Callable Cloud Function: lookupUserByPhone ---");

    // Setup Bob with phone number and phone verification in his private profile
    await requestFirestore(`users/${BOB_UID}`, 'PATCH', toFirestoreFields({
        userId: BOB_UID,
        phoneNumber: "+15559876543",
        displayName: "Bob Guardian",
        fortId: "@bob.fort",
        discoverableByPhone: true
    }), BOB_UID, { phone_number: "+15559876543" });

    // Setup Charlie with unlisted phone (discoverableByPhone = false)
    await requestFirestore(`users/${CHARLIE_UID}`, 'PATCH', toFirestoreFields({
        userId: CHARLIE_UID,
        phoneNumber: "+15550001111",
        displayName: "Charlie Hidden",
        fortId: "@charlie.fort",
        discoverableByPhone: false
    }), CHARLIE_UID, { phone_number: "+15550001111" });

    // Seed a legacy/corrupt profile to verify the callable does not treat this client data as proof.
    await adminDb.collection("users").doc(EVE_UID).set({
        userId: EVE_UID,
        phoneNumber: "+15559876543",
        displayName: "Eve Spoof",
        discoverableByPhone: true
    });

    // 5.1 A profile phone number without a signed phone claim is not verification.
    const unverifiedLookupRes = await callFunction("lookupUserByPhone", { phoneNumber: "+15559876543" }, EVE_UID);
    assert(
        unverifiedLookupRes.status === 403 || (unverifiedLookupRes.data && unverifiedLookupRes.data.error && unverifiedLookupRes.data.error.status === 'PERMISSION_DENIED'),
        "Requester without verified phone is REJECTED by phone lookup (PERMISSION_DENIED)"
    );

    // 5.2 Verified requester (Alice has phoneNumber in users/alice_user_1) searches Bob
    const aliceLookupBobRes = await callFunction(
        "lookupUserByPhone", { phoneNumber: "+1 (555) 987-6543" }, ALICE_UID,
        { phone_number: "+15551234567" }
    );
    const bobFound = aliceLookupBobRes.data && aliceLookupBobRes.data.result && aliceLookupBobRes.data.result.user;
    assert(
        aliceLookupBobRes.status === 200 && bobFound != null && bobFound.userId === BOB_UID && bobFound.displayName === "Bob Guardian",
        "Verified requester discovers discoverable peer by normalized phone"
    );
    assert(
        bobFound != null && bobFound.phoneNumber === undefined && bobFound.phoneHash === undefined,
        "Phone lookup returns only minimal public profile; no phone number or reusable hashes exposed"
    );

    // 5.3 Target with discoverableByPhone = false returns null
    const aliceLookupCharlieRes = await callFunction(
        "lookupUserByPhone", { phoneNumber: "+15550001111" }, ALICE_UID,
        { phone_number: "+15551234567" }
    );
    const charlieFound = aliceLookupCharlieRes.data && aliceLookupCharlieRes.data.result && aliceLookupCharlieRes.data.result.user;
    assert(
        aliceLookupCharlieRes.status === 200 && charlieFound === null,
        "Target user with discoverableByPhone: false is NOT discoverable (returns null)"
    );

    // 5.4 Target who blocked requester returns null
    // Bob blocks Alice
    await requestFirestore(`users/${BOB_UID}/blocklist/${ALICE_UID}`, 'PATCH', toFirestoreFields({
        blockedUserId: ALICE_UID,
        createdAt: Date.now()
    }), BOB_UID);

    const aliceLookupBlockedBobRes = await callFunction(
        "lookupUserByPhone", { phoneNumber: "+15559876543" }, ALICE_UID,
        { phone_number: "+15551234567" }
    );
    const blockedBobFound = aliceLookupBlockedBobRes.data && aliceLookupBlockedBobRes.data.result && aliceLookupBlockedBobRes.data.result.user;
    assert(
        aliceLookupBlockedBobRes.status === 200 && blockedBobFound === null,
        "Target user who blocked requester is NOT discoverable (returns null)"
    );

    // Unblock Alice so subsequent legitimate calls and messages can proceed
    await requestFirestore(`users/${BOB_UID}/blocklist/${ALICE_UID}`, 'DELETE', null, BOB_UID);

    // Legacy client-writable phone data must never shadow Firebase Auth's verified phone index.
    const aliceLookupEveSpoofRes = await callFunction(
        "lookupUserByPhone", { phoneNumber: "+15559876543" }, ALICE_UID,
        { phone_number: "+15551234567" }
    );
    const eveSpoofFound = aliceLookupEveSpoofRes.data && aliceLookupEveSpoofRes.data.result && aliceLookupEveSpoofRes.data.result.user;
    assert(
        aliceLookupEveSpoofRes.status === 200 && eveSpoofFound != null && eveSpoofFound.userId === BOB_UID,
        "Phone lookup resolves the Firebase Auth owner, not a forged profile phone number"
    );

    // 5.5 Rate limiting test: excessive lookups trigger RESOURCE_EXHAUSTED
    let rateLimited = false;
    for (let i = 0; i < 12; i++) {
        const res = await callFunction(
            "lookupUserByPhone", { phoneNumber: `+1555999000${i}` }, ALICE_UID,
            { phone_number: "+15551234567" }
        );
        if (res.status === 429 || (res.data && res.data.error && res.data.error.status === 'RESOURCE_EXHAUSTED')) {
            rateLimited = true;
            break;
        }
    }
    assert(rateLimited, "Phone discovery enforces strict server-side rate limiting against enumeration");

    // TURN credentials require authentication and use a short-lived Coturn REST HMAC credential.
    const unauthTurnRes = await callFunction("getTurnCredentials", {}, null);
    assert(
        unauthTurnRes.status === 401 || (unauthTurnRes.data && unauthTurnRes.data.error && unauthTurnRes.data.error.status === "UNAUTHENTICATED"),
        "Unauthenticated clients cannot obtain TURN credentials"
    );
    const turnRes = await callFunction("getTurnCredentials", {}, ALICE_UID);
    const turnServers = turnRes.data && turnRes.data.result && turnRes.data.result.servers;
    const configuredTurnHosts = (process.env.FORT_TURN_HOSTS || "").split(",").map((x) => x.trim()).filter(Boolean);
    if (configuredTurnHosts.length > 0 && process.env.FORT_TURN_SHARED_SECRET) {
        const server = turnServers && turnServers[0];
        const expectedCredential = server
            ? crypto.createHmac("sha1", process.env.FORT_TURN_SHARED_SECRET).update(server.username).digest("base64")
            : "";
        assert(
            turnRes.status === 200 && server != null
                && JSON.stringify(server.urls) === JSON.stringify(configuredTurnHosts)
                && server.username.endsWith(`:${ALICE_UID}`)
                && server.credential === expectedCredential
                && server.expiresAt > Math.floor(Date.now() / 1000),
            "Authenticated client receives valid short-lived TURN REST credentials"
        );
    } else {
        assert(
            turnRes.status === 200 && Array.isArray(turnServers) && turnServers.length === 0,
            "TURN credentials stay disabled until server-side TURN configuration is provided"
        );
    }


    // -----------------------------------------------------------------
    // TEST 6: WebRTC Calls & ICE Candidate Security
    // -----------------------------------------------------------------
    console.log("\n--- 6. Testing WebRTC Call Signaling & ICE Candidate Isolation ---");

    const callId = "call_alice_bob_prod_1";

    // Alice creates call to Bob
    const callDoc = toFirestoreFields({
        callId: callId,
        callerUserId: ALICE_UID,
        callerDisplayName: "Alice",
        receiverUserId: BOB_UID,
        callType: "AUDIO",
        status: "RINGING"
    });
    const createCallRes = await requestFirestore(`calls/${callId}`, 'PATCH', callDoc, ALICE_UID);
    assert(createCallRes.status === 200, "Alice can initiate call to Bob in /calls");

    // Eve cannot read call
    const eveReadCallRes = await requestFirestore(`calls/${callId}`, 'GET', null, EVE_UID);
    assert(eveReadCallRes.status === 403, "Eve CANNOT read Alice & Bob's call document (403 Forbidden)");

    // Alice writes ICE candidate
    const candId1 = "cand_alice_1";
    const aliceCand = toFirestoreFields({
        candidate: "candidate:1 1 UDP 2122260223 192.168.1.100 54321 typ host",
        sdpMid: "0",
        sdpMLineIndex: 0,
        isCaller: true
    });
    const createCandRes = await requestFirestore(`calls/${callId}/candidates/${candId1}`, 'PATCH', aliceCand, ALICE_UID);
    assert(createCandRes.status === 200, "Alice (caller) can write ICE candidate");

    // Bob reads ICE candidate
    const bobReadCandRes = await requestFirestore(`calls/${callId}/candidates/${candId1}`, 'GET', null, BOB_UID);
    assert(bobReadCandRes.status === 200, "Bob (receiver) can read Alice's ICE candidate");

    // Eve cannot read or inject ICE candidate
    const eveReadCandRes = await requestFirestore(`calls/${callId}/candidates/${candId1}`, 'GET', null, EVE_UID);
    assert(eveReadCandRes.status === 403, "Eve CANNOT read call candidates for Alice & Bob's call (403 Forbidden)");

    const eveCand = toFirestoreFields({
        candidate: "candidate:fake",
        sdpMid: "0",
        sdpMLineIndex: 0,
        isCaller: false
    });
    const eveInjectCandRes = await requestFirestore(`calls/${callId}/candidates/cand_eve`, 'PATCH', eveCand, EVE_UID);
    assert(eveInjectCandRes.status === 403, "Eve CANNOT inject ICE candidate into call (403 Forbidden)");


    // -----------------------------------------------------------------
    // TEST 7: Cross-User Writes in Messages and Knock First Requests
    // -----------------------------------------------------------------
    console.log("\n--- 7. Testing Cross-User Spoofing & Message Isolation ---");

    const messageId = "msg_e2ee_prod_001";
    // Eve attempts to spoof Alice as sender of a message to Bob (MUST BE REJECTED)
    const spoofedMsg = toFirestoreFields({
        messageId: messageId,
        senderUserId: ALICE_UID, // Spoofed! Auth is Eve!
        recipientUserId: BOB_UID,
        ciphertextBase64: "c3Bvb2ZlZA==",
        ivBase64: "aXZpdmk=",
        ephemeralKeyBase64: "ZXBoZXBt"
    });
    const eveSpoofMsgRes = await requestFirestore(`messages/${messageId}`, 'PATCH', spoofedMsg, EVE_UID);
    assert(eveSpoofMsgRes.status === 403, "Eve CANNOT send spoofed message claiming to be Alice (403 Forbidden)");

    // Alice sends legitimate message to Bob
    const legitMsg = toFirestoreFields({
        messageId: messageId,
        senderUserId: ALICE_UID,
        recipientUserId: BOB_UID,
        ciphertextBase64: "Y2lwaGVydGV4dA==",
        ivBase64: "aXZiYXNlNjQ=",
        ephemeralKeyBase64: "ZXBoZW1lcmFs"
    });
    const aliceSendMsgRes = await requestFirestore(`messages/${messageId}`, 'PATCH', legitMsg, ALICE_UID);
    assert(aliceSendMsgRes.status === 200, "Alice can send legitimate encrypted message to Bob");

    // Eve cannot read Alice's message to Bob
    const eveReadMsgRes = await requestFirestore(`messages/${messageId}`, 'GET', null, EVE_UID);
    assert(eveReadMsgRes.status === 403, "Eve CANNOT read encrypted message between Alice and Bob (403 Forbidden)");

    // Bob reads the message
    const bobReadMsgRes = await requestFirestore(`messages/${messageId}`, 'GET', null, BOB_UID);
    assert(bobReadMsgRes.status === 200, "Bob can read message addressed to him");

    // Eve attempts to spoof Knock First request as Alice
    const spoofKnock = toFirestoreFields({
        requestId: "knock_spoof_1",
        senderUserId: ALICE_UID,
        recipientUserId: BOB_UID,
        status: "PENDING"
    });
    const eveSpoofKnockRes = await requestFirestore(`knock_first/knock_spoof_1`, 'PATCH', spoofKnock, EVE_UID);
    assert(eveSpoofKnockRes.status === 403, "Eve CANNOT spoof Knock First request as Alice (403 Forbidden)");

    console.log("\n=================================================");
    console.log(`Results: ${passedTests} passed, ${failedTests} failed`);
    console.log("=================================================");

    if (failedTests > 0) {
        process.exit(1);
    }
}

runTests().catch(err => {
    console.error("Test runner error:", err);
    process.exit(1);
});
