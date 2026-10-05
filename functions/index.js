const functions = require("firebase-functions");
const admin = require("firebase-admin");
const crypto = require("crypto");

admin.initializeApp();
const db = admin.firestore();

function sha256Hex(str) {
  return crypto.createHash("sha256").update(str, "utf8").digest("hex");
}

function normalizeE164(phone) {
  if (!phone || typeof phone !== "string") return null;
  const digits = phone.replace(/[^\d+]/g, "");
  if (!digits) return null;
  const formatted = digits.startsWith("+") ? digits : `+${digits}`;
  if (!/^\+[1-9]\d{7,14}$/.test(formatted)) return null;
  return formatted;
}

/**
 * Trusted Callable Cloud Function: claimContactPass
 *
 * Atomically claims a contact pass and token record in a Firestore transaction.
 * - Authenticated claimant derived strictly from context.auth.uid
 * - Hashed token validation against pass_tokens collection
 * - Verification of pass expiration, revocation, single-use state, and blocklists
 * - Returns only minimal details needed for local connection creation
 */
exports.claimContactPass = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication is required to claim a contact pass."
    );
  }

  const claimantUid = context.auth.uid;
  const rawToken = data && data.token;
  if (!rawToken || typeof rawToken !== "string" || !rawToken.trim()) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "A valid invitation token is required."
    );
  }

  const cleanToken = rawToken.trim().toUpperCase();
  const tokenHash = sha256Hex(cleanToken);
  const altHash = cleanToken.startsWith("PASS-")
    ? sha256Hex(cleanToken.replace("PASS-", ""))
    : sha256Hex(`PASS-${cleanToken}`);

  return await db.runTransaction(async (transaction) => {
    // 1. Locate pass token record
    let tokenRef = db.collection("pass_tokens").doc(tokenHash);
    let tokenDoc = await transaction.get(tokenRef);

    if (!tokenDoc.exists) {
      const altRef = db.collection("pass_tokens").doc(altHash);
      const altDoc = await transaction.get(altRef);
      if (altDoc.exists) {
        tokenRef = altRef;
        tokenDoc = altDoc;
      } else {
        throw new functions.https.HttpsError(
          "not-found",
          "Pass not found. Check the invitation and try again."
        );
      }
    }

    const tokenData = tokenDoc.data() || {};
    const passId = tokenData.passId;
    if (!passId) {
      throw new functions.https.HttpsError(
        "not-found",
        "Pass record reference is missing."
      );
    }

    // 2. Locate contact pass document
    const passRef = db.collection("contact_passes").doc(passId);
    const passDoc = await transaction.get(passRef);
    if (!passDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Pass not found. Check the invitation and try again."
      );
    }

    const passData = passDoc.data() || {};
    const issuerUserId = passData.issuerUserId;

    // 3. Prevent self-claim
    if (issuerUserId === claimantUid) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "You cannot claim your own pass."
      );
    }

    // 4. Check blocklist: claimant cannot claim if blocked by issuer
    const blockRef = db
      .collection("users")
      .doc(issuerUserId)
      .collection("blocklist")
      .doc(claimantUid);
    const blockDoc = await transaction.get(blockRef);
    if (blockDoc.exists) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "You cannot claim this pass."
      );
    }

    // 5. Expiration check
    const now = Date.now();
    const expiresAt = Number(passData.expiresAt || tokenData.expiresAt || 0);
    if (expiresAt > 0 && expiresAt <= now) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "This pass has expired."
      );
    }

    // 6. Revocation check
    if (passData.isRevoked === true || tokenData.isRevoked === true) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "This pass has been revoked."
      );
    }

    // 7. Single-use and existing claim verification
    const isSingleUse = passData.isSingleUse !== false;
    if (isSingleUse) {
      if (passData.isClaimed === true && passData.claimantUserId !== claimantUid) {
        throw new functions.https.HttpsError(
          "already-exists",
          "This single-use pass was already claimed."
        );
      }
      if (tokenData.isClaimed === true && tokenData.claimantUserId && tokenData.claimantUserId !== claimantUid) {
        throw new functions.https.HttpsError(
          "already-exists",
          "This single-use pass was already claimed."
        );
      }
    }

    // 8. Atomically update both records
    const claimUpdate = {
      isClaimed: true,
      claimantUserId: claimantUid,
      claimedAt: now,
    };
    transaction.update(passRef, claimUpdate);
    transaction.update(tokenRef, claimUpdate);

    // 9. Fetch issuer public key for connection setup
    let issuerPublicKey = passData.issuerPublicKey || "";
    if (!issuerPublicKey) {
      const cardType = passData.cardType || "PERSONAL";
      const cardRef = db
        .collection("users")
        .doc(issuerUserId)
        .collection("cards")
        .doc(cardType);
      const cardDoc = await transaction.get(cardRef);
      if (cardDoc.exists) {
        issuerPublicKey = cardDoc.data().publicKey || "";
      }
    }

    // 10. Return strictly minimal details (no private account info, email, or phone)
    return {
      passId: passData.passId || passId,
      issuerUserId: passData.issuerUserId,
      cardType: passData.cardType || "PERSONAL",
      durationType: passData.durationType || "SEVEN_DAYS",
      expiresAt: expiresAt,
      isSingleUse: isSingleUse,
      issuerPublicKey: issuerPublicKey,
    };
  });
});

/**
 * Trusted Callable Cloud Function: lookupUserByPhone
 *
 * Privacy-preserving phone number discovery:
 * - Requester must be authenticated and have a verified phone number
 * - Strictly rate limited per requester to prevent enumeration/scraping
 * - Normalizes candidate numbers server-side
 * - Honors recipient discoverableByPhone setting and blocklist
 * - Returns only minimal user search result without exposing hashes or raw numbers
 */
exports.lookupUserByPhone = functions.https.onCall(async (data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication is required to search users."
    );
  }

  const requesterUid = context.auth.uid;

  // Only Firebase Auth's signed token proves phone verification. Profile fields are client data.
  const verifiedRequesterPhone = context.auth.token.phone_number;
  if (typeof verifiedRequesterPhone !== "string" || !/^\+[1-9]\d{7,14}$/.test(verifiedRequesterPhone)) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Phone verification is required before you can discover peers by phone."
    );
  }

  // 2. Enforce strict server-side rate limiting (max 10 lookups per 15 minutes per user)
  const now = Date.now();
  const windowMs = 15 * 60 * 1000;
  const maxLookups = 10;
  const rateLimitRef = db.collection("phone_lookup_limits").doc(requesterUid);

  await db.runTransaction(async (transaction) => {
    const rateLimitDoc = await transaction.get(rateLimitRef);
    const limitData = rateLimitDoc.exists ? rateLimitDoc.data() : { count: 0, windowStart: now };
    
    if (!rateLimitDoc.exists || (now - (limitData.windowStart || 0) > windowMs)) {
      transaction.set(rateLimitRef, { count: 1, windowStart: now });
    } else if (limitData.count >= maxLookups) {
      throw new functions.https.HttpsError(
        "resource-exhausted",
        "Rate limit exceeded for phone discovery. Please wait before searching again."
      );
    } else {
      transaction.set(rateLimitRef, { count: (limitData.count || 0) + 1, windowStart: limitData.windowStart || now }, { merge: true });
    }
  });

  // 3. Normalize input phone number server-side
  const rawPhone = data && data.phoneNumber;
  const normalized = normalizeE164(rawPhone);
  if (!normalized) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Invalid phone number format. Include country code (e.g. +1234567890)."
    );
  }

  // Resolve the target through Firebase Auth's verified phone index, never a client-writable profile field.
  let targetAuthUser;
  try {
    targetAuthUser = await admin.auth().getUserByPhoneNumber(normalized);
  } catch (error) {
    if (error && error.code === "auth/user-not-found") {
      return { user: null };
    }
    throw error;
  }

  const targetUid = targetAuthUser.uid;
  const targetDoc = await db.collection("users").doc(targetUid).get();
  if (!targetDoc.exists) {
    return { user: null };
  }
  const targetData = targetDoc.data() || {};

  // Cannot discover self
  if (targetUid === requesterUid) {
    return { user: null };
  }

  // 5. Honor recipient's phone discovery preference
  if (targetData.discoverableByPhone === false) {
    return { user: null };
  }

  // 6. Check if target user blocked requester
  const blockDoc = await db
    .collection("users")
    .doc(targetUid)
    .collection("blocklist")
    .doc(requesterUid)
    .get();

  if (blockDoc.exists) {
    return { user: null };
  }

  // 7. Return only minimal, privacy-safe discovery profile
  const displayName = targetData.displayName || "Sovereign User";
  const fortId =
    targetData.fortId ||
    `@${displayName.toLowerCase().replace(/\s+/g, "")}.fort`;

  return {
    user: {
      userId: targetUid,
      displayName: displayName,
      fortId: fortId,
      avatarEmoji: "🛡️",
      hasVerifiedPhone: true,
    },
  };
});


/**
 * Returns short-lived Coturn REST credentials. Configure FORT_TURN_HOSTS and
 * FORT_TURN_SHARED_SECRET in the Functions environment; secrets never go to the APK.
 */
exports.getTurnCredentials = functions.https.onCall(async (_data, context) => {
  if (!context.auth || !context.auth.uid) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication is required to obtain call network credentials."
    );
  }

  const secret = process.env.FORT_TURN_SHARED_SECRET || "";
  const urls = (process.env.FORT_TURN_HOSTS || "")
    .split(",")
    .map((url) => url.trim())
    .filter(Boolean);

  if (!secret || urls.length === 0) {
    return { servers: [] };
  }
  if (urls.some((url) => !/^turns?:[^\s]+$/i.test(url))) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "TURN service configuration is invalid."
    );
  }

  const requestedTtl = Number.parseInt(process.env.FORT_TURN_TTL_SECONDS || "3600", 10);
  const ttlSeconds = Number.isFinite(requestedTtl)
    ? Math.min(Math.max(requestedTtl, 300), 86400)
    : 3600;
  const expiresAt = Math.floor(Date.now() / 1000) + ttlSeconds;
  const username = `${expiresAt}:${context.auth.uid}`;
  const credential = crypto
    .createHmac("sha1", secret)
    .update(username)
    .digest("base64");

  return {
    servers: [{ urls, username, credential, expiresAt }],
  };
});
