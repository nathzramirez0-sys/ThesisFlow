import { getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { logger } from "firebase-functions/v2";
import { onDocumentDeleted } from "firebase-functions/v2/firestore";
import { Collections } from "./shared";

/**
 * When a leader deletes a chapter, removes what hangs off it: the version
 * history (a subcollection Firestore doesn't delete with its parent), the
 * files records, and the files themselves in Cloud Storage.
 *
 * The client only deletes one document, so deleting a chapter works offline;
 * this cleanup runs once the deletion reaches the server.
 */
export const cleanUpChapter = onDocumentDeleted(
  `${Collections.groups}/{groupId}/${Collections.chapters}/{chapterId}`,
  async (event) => {
    const { groupId, chapterId } = event.params;
    const db = getFirestore();
    const groupRef = db.collection(Collections.groups).doc(groupId);

    await db.recursiveDelete(groupRef.collection(Collections.chapters).doc(chapterId));

    const files = await groupRef.collection(Collections.files).where("chapterId", "==", chapterId).get();
    const bucket = getStorage().bucket();
    await Promise.all(
      files.docs.map(async (file) => {
        const path = file.get("storagePath");
        if (typeof path === "string") await bucket.file(path).delete({ ignoreNotFound: true });
        await file.ref.delete();
      }),
    );
    logger.info("Cleaned up deleted chapter", { groupId, chapterId, files: files.size });
  },
);
