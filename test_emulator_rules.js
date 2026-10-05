const FIRESTORE_PORT = 8080;
const PROJECT_ID = "fort-chat-f3308";
const BASE_URL = `http://127.0.0.1:${FIRESTORE_PORT}/v1/projects/${PROJECT_ID}/databases/(default)/documents`;

function makeMockJwt(uid) {
    if (!uid) return null;
    const header = Buffer.from(JSON.stringify({ alg: "none", typ: "JWT" })).toString('base64url');
    const payload = Buffer.from(JSON.stringify({
        user_id: uid,
        sub: uid,
        aud: PROJECT_ID,
        iss: `https://securetoken.google.com/${PROJECT_ID}`
    })).toString('base64url');
    return `${header}.${payload}.`;
}

function makeAuthHeader(uid) {
    if (!uid) return {};
    return { 'Authorization': `Bearer ${makeMockJwt(uid)}` };
}

async function requestFirestore(path, method = 'GET', body = null, uid = null) {
    const url = `${BASE_URL}/${path}`;
    const headers = {
        'Content-Type': 'application/json',
        ...makeAuthHeader(uid)
    };
    const options = { method, headers };
    if (body) {
        options.body = JSON.stringify(body);
    }
    const res = await fetch(url, options);
    const data = await res.json().catch(() => ({}));
    return { status: res.status, data };
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
    console.log("Starting Fort Firebase Emulator Rules Test Suite");
    console.log("=================================================\n");

    const ALICE_UID = "alice_user_1";
    const BOB_UID = "bob_user_2";
    const EVE_UID = "eve_attacker_3";

    // -----------------------------------------------------------------
    // TEST 1: Private User Profiles vs Minimal Public Profiles
    // -----------------------------------------------------------------
    console.log("--- 1. Testing User Profile Privacy & Public Profiles ---");
    
    // Alice creates private profile
    const alicePrivateProfile = toFirestoreFields({
        userId: ALICE_UID,
        email: "alice@secret.vault",
        phoneNumber: "+15551234567",
        displayName: "Alice Sovereign",
        authToken: "tok_private_123"
    });
    const createPrivateRes = await requestFirestore(`users/${ALICE_UID}`, 'PATCH', alicePrivateProfile, ALICE_UID);
    assert(createPrivateRes.status === 200, "Alice can create her own private /users document");

    // Alice creates minimal public profile
    const alicePublicProfile = toFirestoreFields({
        userId: ALICE_UID,
        displayName: "Alice Sovereign",
        normalizedDisplayName: "alice sovereign",
        fortId: "@alice.fort",
        phoneHash: "hash_alice_123",
        hasVerifiedPhone: true,
        discoverableByName: true,
        discoverableByPhone: true
    });
    const createPublicRes = await requestFirestore(`public_profiles/${ALICE_UID}`, 'PATCH', alicePublicProfile, ALICE_UID);
    assert(createPublicRes.status === 200, "Alice can create her public profile in /public_profiles");

    // Alice reads her private profile
    const aliceReadPrivateRes = await requestFirestore(`users/${ALICE_UID}`, 'GET', null, ALICE_UID);
    assert(aliceReadPrivateRes.status === 200, "Alice can read her own private /users document");

    // Eve attempts to read Alice's private profile (MUST BE REJECTED)
    const eveReadPrivateRes = await requestFirestore(`users/${ALICE_UID}`, 'GET', null, EVE_UID);
    assert(eveReadPrivateRes.status === 403, "Eve CANNOT read Alice's private /users document (403 Forbidden)");

    // Eve reads Alice's public profile (Allowed)
    const eveReadPublicRes = await requestFirestore(`public_profiles/${ALICE_UID}`, 'GET', null, EVE_UID);
    assert(eveReadPublicRes.status === 200, "Eve CAN read Alice's minimal /public_profiles document");

    // Eve attempts to write to Alice's profile (MUST BE REJECTED)
    const eveTamperProfileRes = await requestFirestore(`users/${ALICE_UID}`, 'PATCH', toFirestoreFields({ email: "pwned@eve.com" }), EVE_UID);
    assert(eveTamperProfileRes.status === 403, "Eve CANNOT write to Alice's /users document (403 Forbidden)");


    // -----------------------------------------------------------------
    // TEST 2: Contact Pass Token Scraping & Atomic Single-Use Claims
    // -----------------------------------------------------------------
    console.log("\n--- 2. Testing Contact Pass Anti-Scraping & Secure Claims ---");

    const passId = "pass_test_001";
    const tokenHash = "token_hash_abc123xyz";
    const passExpiresAt = Date.now() + 86400000;

    // Alice creates contact pass
    const passDoc = toFirestoreFields({
        passId: passId,
        issuerUserId: ALICE_UID,
        token: "PASS-TOKEN-SECRET",
        isRevoked: false,
        isClaimed: false,
        isSingleUse: true,
        expiresAt: passExpiresAt
    });
    const createPassRes = await requestFirestore(`contact_passes/${passId}`, 'PATCH', passDoc, ALICE_UID);
    assert(createPassRes.status === 200, "Alice can create a contact pass");

    // Alice creates the token record in /pass_tokens/{tokenHash}
    const tokenDoc = toFirestoreFields({
        tokenHash: tokenHash,
        passId: passId,
        issuerUserId: ALICE_UID,
        token: "PASS-TOKEN-SECRET",
        isRevoked: false,
        isClaimed: false,
        isSingleUse: true,
        expiresAt: passExpiresAt
    });
    const createTokenRes = await requestFirestore(`pass_tokens/${tokenHash}`, 'PATCH', tokenDoc, ALICE_UID);
    assert(createTokenRes.status === 200, "Alice can publish pass token in /pass_tokens/{tokenHash}");

    // Eve attempts to read Alice's pass from /contact_passes (MUST BE REJECTED - Anti Scraping)
    const eveScrapePassRes = await requestFirestore(`contact_passes/${passId}`, 'GET', null, EVE_UID);
    assert(eveScrapePassRes.status === 403, "Eve CANNOT scrape Alice's document in /contact_passes (403 Forbidden)");

    // Bob (legitimate token recipient) reads /pass_tokens/{tokenHash} by knowing the token hash
    const bobReadTokenRes = await requestFirestore(`pass_tokens/${tokenHash}`, 'GET', null, BOB_UID);
    assert(bobReadTokenRes.status === 200, "Bob can read pass token by direct hash lookup");

    // Bob claims the pass atomically
    const claimUpdate = {
        fields: {
            ...tokenDoc.fields,
            isClaimed: { booleanValue: true },
            claimantUserId: { stringValue: BOB_UID }
        }
    };
    const bobClaimRes = await requestFirestore(`pass_tokens/${tokenHash}?updateMask.fieldPaths=isClaimed&updateMask.fieldPaths=claimantUserId`, 'PATCH', claimUpdate, BOB_UID);
    assert(bobClaimRes.status === 200, "Bob can atomically claim unclaimed pass");

    // Eve attempts to claim the same pass again (MUST BE REJECTED - Single-Use Enforcement)
    const eveClaimUpdate = {
        fields: {
            ...tokenDoc.fields,
            isClaimed: { booleanValue: true },
            claimantUserId: { stringValue: EVE_UID }
        }
    };
    const eveDoubleClaimRes = await requestFirestore(`pass_tokens/${tokenHash}?updateMask.fieldPaths=isClaimed&updateMask.fieldPaths=claimantUserId`, 'PATCH', eveClaimUpdate, EVE_UID);
    assert(eveDoubleClaimRes.status === 403, "Eve CANNOT claim already-claimed pass (403 Forbidden)");


    // -----------------------------------------------------------------
    // TEST 3: WebRTC Calls & ICE Candidate Security
    // -----------------------------------------------------------------
    console.log("\n--- 3. Testing WebRTC Call Signaling & ICE Candidate Isolation ---");

    const callId = "call_alice_bob_123";

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

    // Eve attempts to read the call (MUST BE REJECTED)
    const eveReadCallRes = await requestFirestore(`calls/${callId}`, 'GET', null, EVE_UID);
    assert(eveReadCallRes.status === 403, "Eve CANNOT read Alice & Bob's call signaling document (403 Forbidden)");

    // Alice adds ICE candidate
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

    // Eve attempts to read Alice's ICE candidate (MUST BE REJECTED)
    const eveReadCandRes = await requestFirestore(`calls/${callId}/candidates/${candId1}`, 'GET', null, EVE_UID);
    assert(eveReadCandRes.status === 403, "Eve CANNOT read call candidates for Alice & Bob's call (403 Forbidden)");

    // Eve attempts to inject an ICE candidate into Alice & Bob's call (MUST BE REJECTED)
    const eveCand = toFirestoreFields({
        candidate: "candidate:fake",
        sdpMid: "0",
        sdpMLineIndex: 0,
        isCaller: false
    });
    const eveInjectCandRes = await requestFirestore(`calls/${callId}/candidates/cand_eve`, 'PATCH', eveCand, EVE_UID);
    assert(eveInjectCandRes.status === 403, "Eve CANNOT inject ICE candidate into call (403 Forbidden)");

    // Eve attempts to modify participant identities in the call (MUST BE REJECTED)
    const tamperCall = {
        fields: {
            ...callDoc.fields,
            receiverUserId: { stringValue: EVE_UID }
        }
    };
    const eveTamperCallRes = await requestFirestore(`calls/${callId}`, 'PATCH', tamperCall, EVE_UID);
    assert(eveTamperCallRes.status === 403, "Eve CANNOT alter call participant identities (403 Forbidden)");


    // -----------------------------------------------------------------
    // TEST 4: Cross-User Writes in Messages and Knock First Requests
    // -----------------------------------------------------------------
    console.log("\n--- 4. Testing Cross-User Spoofing & Message Isolation ---");

    const messageId = "msg_e2ee_001";
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

    // Eve attempts to read Alice's message to Bob (MUST BE REJECTED)
    const eveReadMsgRes = await requestFirestore(`messages/${messageId}`, 'GET', null, EVE_UID);
    assert(eveReadMsgRes.status === 403, "Eve CANNOT read encrypted message between Alice and Bob (403 Forbidden)");

    // Bob reads the message (Allowed)
    const bobReadMsgRes = await requestFirestore(`messages/${messageId}`, 'GET', null, BOB_UID);
    assert(bobReadMsgRes.status === 200, "Bob can read message addressed to him");

    // Eve attempts to spoof Knock First request as Alice (MUST BE REJECTED)
    const spoofKnock = toFirestoreFields({
        requestId: "knock_spoof_1",
        senderUserId: ALICE_UID, // Spoofed!
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
