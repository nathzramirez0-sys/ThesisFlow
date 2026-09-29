import { DocumentReference, getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { logger } from "firebase-functions/v2";
import { onDocumentDeleted } from "firebase-functions/v2/firestore";
import { Collections } from "./shared";

/**
 * Removes everything that hangs off a deleted document: its subcollections
 * (Firestore doesn't delete those with the parent), the files records that
 * point at it, and the files themselves in Cloud Storage.
 *
 * The client only deletes one document, so deleting works offline; this
 * cleanup runs once the deletion reaches the server.
 */
async function cleanUp(groupId: string, deleted: DocumentReference, fileField: "chapterId" | "taskId" | "feedbackId") {
  const db = getFirestore();
  await db.recursiveDelete(deleted);

  const groupRef = db.collection(Collections.groups).doc(groupId);
  const files = await groupRef.collection(Collections.files).where(fileField, "==", deleted.id).get();
  const bucket = getStorage().bucket();
  await Promise.all(
    files.docs.map(async (file) => {
      const path = file.get("storagePath");
      if (typeof path === "string") await bucket.file(path).delete({ ignoreNotFound: true });
      await file.ref.delete();
    }),
  );
  return files.size;
}

/** A deleted chapter takes its version history, feedback and every file on either with it. */
export const cleanUpChapter = onDocumentDeleted(
  `${Collections.groups}/{groupId}/${Collections.chapters}/{chapterId}`,
  async (event) => {
    const { groupId, chapterId } = event.params;
    const ref = getFirestore().doc(`${Collections.groups}/${groupId}/${Collections.chapters}/${chapterId}`);
    const files = await cleanUp(groupId, ref, "chapterId");
    logger.info("Cleaned up deleted chapter", { groupId, chapterId, files });
  },
);

/** A deleted task takes its comments and attachments with it. */
export const cleanUpTask = onDocumentDeleted(
  `${Collections.groups}/{groupId}/${Collections.tasks}/{taskId}`,
  async (event) => {
    const { groupId, taskId } = event.params;
    const ref = getFirestore().doc(`${Collections.groups}/${groupId}/${Collections.tasks}/${taskId}`);
    const files = await cleanUp(groupId, ref, "taskId");
    logger.info("Cleaned up deleted task", { groupId, taskId, files });
  },
);

/** Deleted feedback takes the adviser's files with it. */
export const cleanUpFeedback = onDocumentDeleted(
  `${Collections.groups}/{groupId}/${Collections.chapters}/{chapterId}/${Collections.feedback}/{feedbackId}`,
  async (event) => {
    const { groupId, chapterId, feedbackId } = event.params;
    const ref = getFirestore().doc(
      `${Collections.groups}/${groupId}/${Collections.chapters}/${chapterId}/${Collections.feedback}/${feedbackId}`,
    );
    const files = await cleanUp(groupId, ref, "feedbackId");
    logger.info("Cleaned up deleted feedback", { groupId, chapterId, feedbackId, files });
  },
);
