import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { Collections } from "./shared";

/**
 * Keeps the name and photo shown in each group's member list in step with the
 * user's profile. The copies exist so users/{uid} can stay private to its owner.
 */
export const syncMemberProfiles = onDocumentUpdated(`${Collections.users}/{uid}`, async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!before || !after) return;
  if (before.displayName === after.displayName && before.photoUrl === after.photoUrl) return;

  const uid = event.params.uid;
  const db = getFirestore();
  const groups = await db.collection(Collections.groups).where("memberIds", "array-contains", uid).get();

  const writer = db.bulkWriter();
  // A member entry can vanish mid-update (they just left); skip it rather than retry.
  writer.onWriteError((error) => {
    logger.warn("Skipped a member profile copy", { path: error.documentRef.path, code: error.code });
    return false;
  });
  groups.forEach((group) => {
    writer.update(group.ref.collection(Collections.members).doc(uid), {
      displayName: after.displayName ?? "",
      photoUrl: after.photoUrl ?? null,
    });
  });
  await writer.close();
});
