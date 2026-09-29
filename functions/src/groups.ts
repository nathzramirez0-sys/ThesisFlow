import { getFirestore } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { Collections, fail, requireString, requireUid } from "./shared";

/**
 * Deletes a group with every subcollection under it. Clients can't do this
 * themselves: Firestore doesn't delete subcollections along with their parent.
 */
export const deleteGroup = onCall(async (request) => {
  const uid = requireUid(request);
  const groupId = requireString(request.data?.groupId, "groupId");

  const db = getFirestore();
  const groupRef = db.collection(Collections.groups).doc(groupId);
  const group = await groupRef.get();
  if (!group.exists) return { deleted: true }; // Already gone: deleting twice is fine.

  const roles = (group.get("roles") ?? {}) as Record<string, string>;
  if (roles[uid] !== "leader") fail("permission-denied", "Only leaders can delete the group.");

  // Retire the group's invite codes first, so none can point at a deleted group.
  const invites = await groupRef.collection(Collections.invites).get();
  const batch = db.batch();
  invites.forEach((invite) => {
    const code = invite.get("code");
    if (typeof code === "string") batch.delete(db.collection(Collections.inviteCodes).doc(code));
  });
  await batch.commit();

  await db.recursiveDelete(groupRef);
  return { deleted: true };
});
