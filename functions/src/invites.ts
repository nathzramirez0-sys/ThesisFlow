import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import {
  Collections,
  INVITE_TTL_MS,
  MAX_MEMBERS,
  fail,
  isValidCode,
  normalizeCode,
  randomCode,
  requireString,
  requireUid,
} from "./shared";

/**
 * Creates a new invite code for a role and revokes the previous one.
 *
 * Two documents per code: `inviteCodes/{code}` is the lookup table only this
 * server reads, and `groups/{id}/invites/{role}` is what the leader's screen shows.
 */
export const createInvite = onCall(async (request) => {
  const uid = requireUid(request);
  const groupId = requireString(request.data?.groupId, "groupId");
  const role = request.data?.role;
  if (role !== "member" && role !== "adviser") fail("invalid-argument", "role must be member or adviser.");

  const db = getFirestore();
  const groupRef = db.collection(Collections.groups).doc(groupId);
  const inviteRef = groupRef.collection(Collections.invites).doc(role);
  const expiresAt = Timestamp.fromMillis(Date.now() + INVITE_TTL_MS);

  const code = await db.runTransaction(async (tx) => {
    // Transactions need every read before the first write.
    const group = await tx.get(groupRef);
    if (!group.exists) fail("not-found", "Group not found.");
    const roles = (group.get("roles") ?? {}) as Record<string, string>;
    if (roles[uid] !== "leader") fail("permission-denied", "Only leaders can create invites.");

    const previous = await tx.get(inviteRef);

    let fresh: string | undefined;
    for (let attempt = 0; attempt < 5 && !fresh; attempt++) {
      const candidate = randomCode();
      const taken = await tx.get(db.collection(Collections.inviteCodes).doc(candidate));
      if (!taken.exists) fresh = candidate;
    }
    if (!fresh) fail("unavailable", "Could not generate a code. Try again.");

    const oldCode = previous.get("code");
    if (typeof oldCode === "string") {
      tx.set(db.collection(Collections.inviteCodes).doc(oldCode), { revoked: true }, { merge: true });
    }
    tx.set(db.collection(Collections.inviteCodes).doc(fresh), {
      groupId,
      role,
      createdBy: uid,
      createdAt: FieldValue.serverTimestamp(),
      expiresAt,
      revoked: false,
    });
    tx.set(inviteRef, { code: fresh, role, expiresAt, createdAt: FieldValue.serverTimestamp() });
    return fresh;
  });

  return { code, role, expiresAt: expiresAt.toMillis() };
});

/**
 * Adds the caller to the group an invite code belongs to, with the code's role.
 *
 * This runs on the server because a non-member can't read the group, and
 * because letting clients edit `memberIds` would let anyone join any group.
 * Joining a group you're already in simply succeeds, so a double tap is harmless.
 */
export const joinGroup = onCall(async (request) => {
  const uid = requireUid(request);
  const code = normalizeCode(requireString(request.data?.code, "code"));
  if (!isValidCode(code)) fail("not-found", "Invalid invite code.", "INVITE_INVALID");

  const db = getFirestore();
  const codeRef = db.collection(Collections.inviteCodes).doc(code);
  const userRef = db.collection(Collections.users).doc(uid);

  const groupId = await db.runTransaction(async (tx) => {
    const invite = await tx.get(codeRef);
    if (!invite.exists || invite.get("revoked") === true) {
      fail("not-found", "Invalid invite code.", "INVITE_INVALID");
    }
    const expiresAt = invite.get("expiresAt") as Timestamp;
    if (expiresAt.toMillis() <= Date.now()) {
      fail("failed-precondition", "This invite code has expired.", "INVITE_EXPIRED");
    }

    const groupId = invite.get("groupId") as string;
    const role = invite.get("role") as string;
    const groupRef = db.collection(Collections.groups).doc(groupId);
    const [group, user] = await Promise.all([tx.get(groupRef), tx.get(userRef)]);
    if (!group.exists) fail("not-found", "This group no longer exists.", "INVITE_INVALID");

    const memberIds = (group.get("memberIds") ?? []) as string[];
    if (memberIds.includes(uid)) return groupId;
    if (memberIds.length >= MAX_MEMBERS) fail("resource-exhausted", "This group is full.", "GROUP_FULL");

    tx.update(groupRef, {
      memberIds: FieldValue.arrayUnion(uid),
      [`roles.${uid}`]: role,
      updatedAt: FieldValue.serverTimestamp(),
      updatedBy: uid,
    });
    tx.set(groupRef.collection(Collections.members).doc(uid), {
      displayName: user.get("displayName") ?? "",
      photoUrl: user.get("photoUrl") ?? null,
      role,
      joinedAt: FieldValue.serverTimestamp(),
    });
    return groupId;
  });

  return { groupId };
});
