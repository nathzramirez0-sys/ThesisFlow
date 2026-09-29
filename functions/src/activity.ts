import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { onDocumentCreated, onDocumentUpdated, onDocumentWritten } from "firebase-functions/v2/firestore";
import { Collections } from "./shared";

/**
 * The group activity feed. Only these triggers write it (firestore.rules denies
 * every client write), so an entry always describes a change that really
 * reached the server. The actor comes from a field the rules force to be the
 * writer's own uid (updatedBy, uploadedBy, authorId, createdBy), so it can't
 * be forged either.
 */

/** Same strings as ActivityTypes in the Android app's WireFormat.kt. */
type ActivityType =
  | "member_joined"
  | "member_left"
  | "chapter_status"
  | "draft_uploaded"
  | "feedback_posted"
  | "feedback_resolved"
  | "feedback_reopened"
  | "task_created"
  | "task_status";

interface ActivityEntry {
  type: ActivityType;
  actorId: string;
  /** The chapter or task the entry links to. */
  targetId: string | null;
  /** Copied now, like any log: a later rename doesn't rewrite history. */
  targetTitle: string;
  /** Depends on the type: the new status, a version number, or the joiner's role. */
  detail: string | null;
}

/** A TTL policy on expireAt (firestore.indexes.json) deletes entries after this long. */
const ACTIVITY_TTL_MS = 180 * 24 * 60 * 60 * 1000;

// "as const" keeps the literal type, so event.params knows {groupId} and {chapterId}.
const chapterPath = `${Collections.groups}/{groupId}/${Collections.chapters}/{chapterId}` as const;

/**
 * Writes one entry. Triggers run at least once, so the document id is derived
 * from the event id: a retried event rewrites its entry instead of adding a twin.
 */
async function record(groupId: string, entryId: string, entry: ActivityEntry) {
  const group = getFirestore().collection(Collections.groups).doc(groupId);
  await group.collection(Collections.activity).doc(entryId).set({
    ...entry,
    actorName: await nameOf(groupId, entry.actorId),
    createdAt: FieldValue.serverTimestamp(),
    expireAt: Timestamp.fromMillis(Date.now() + ACTIVITY_TTL_MS),
  });
}

/** The group's copy of the name, or the profile's once the member entry is gone (they left). */
async function nameOf(groupId: string, uid: string): Promise<string> {
  const db = getFirestore();
  const member = await db.doc(`${Collections.groups}/${groupId}/${Collections.members}/${uid}`).get();
  const fromGroup = member.get("displayName");
  if (typeof fromGroup === "string" && fromGroup.length > 0) return fromGroup;
  const user = await db.collection(Collections.users).doc(uid).get();
  const fromProfile = user.get("displayName");
  return typeof fromProfile === "string" ? fromProfile : "";
}

async function chapterTitle(groupId: string, chapterId: string): Promise<string> {
  const chapter = await getFirestore().doc(`${Collections.groups}/${groupId}/${Collections.chapters}/${chapterId}`).get();
  const title = chapter.get("title");
  return typeof title === "string" ? title : "";
}

/** Joins (only through joinGroup) and departures (leaving or being removed). */
export const membershipActivity = onDocumentUpdated(`${Collections.groups}/{groupId}`, async (event) => {
  const before = (event.data?.before.get("memberIds") ?? []) as string[];
  const after = (event.data?.after.get("memberIds") ?? []) as string[];
  const roles = (event.data?.after.get("roles") ?? {}) as Record<string, string>;
  const { groupId } = event.params;

  const joined = after.filter((uid) => !before.includes(uid));
  const left = before.filter((uid) => !after.includes(uid));
  await Promise.all([
    ...joined.map((uid) =>
      record(groupId, `${event.id}-${uid}`, {
        type: "member_joined",
        actorId: uid,
        targetId: null,
        targetTitle: "",
        detail: roles[uid] ?? null,
      }),
    ),
    // The app says "is no longer in the group": a removal and a departure look the same here.
    ...left.map((uid) =>
      record(groupId, `${event.id}-${uid}`, {
        type: "member_left",
        actorId: uid,
        targetId: null,
        targetTitle: "",
        detail: null,
      }),
    ),
  ]);
});

/**
 * Chapter status changes. The first draft also moves a chapter from Not started
 * to Drafting; that upload gets its own entry, so the status change is skipped.
 */
export const chapterActivity = onDocumentUpdated(chapterPath, async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!before || !after || before.status === after.status) return;
  if (before.latestVersion !== after.latestVersion) return;
  await record(event.params.groupId, event.id, {
    type: "chapter_status",
    actorId: after.updatedBy,
    targetId: event.params.chapterId,
    targetTitle: after.title ?? "",
    detail: after.status,
  });
});

export const versionActivity = onDocumentCreated(`${chapterPath}/${Collections.versions}/{versionId}`, async (event) => {
  const version = event.data?.data();
  if (!version) return;
  const { groupId, chapterId } = event.params;
  await record(groupId, event.id, {
    type: "draft_uploaded",
    actorId: version.uploadedBy,
    targetId: chapterId,
    targetTitle: await chapterTitle(groupId, chapterId),
    detail: String(version.versionNumber),
  });
});

/** New feedback, and feedback resolved or reopened. Deletions are left to cleanUpFeedback. */
export const feedbackActivity = onDocumentWritten(`${chapterPath}/${Collections.feedback}/{feedbackId}`, async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!after) return;

  let type: ActivityType;
  let actorId: string;
  let detail: string | null = null;
  if (!before) {
    type = "feedback_posted";
    actorId = after.authorId;
    detail = typeof after.versionNumber === "number" ? String(after.versionNumber) : null;
  } else if (before.resolved !== after.resolved) {
    type = after.resolved ? "feedback_resolved" : "feedback_reopened";
    actorId = after.updatedBy;
  } else {
    return;
  }

  const { groupId, chapterId } = event.params;
  await record(groupId, event.id, {
    type,
    actorId,
    targetId: chapterId,
    targetTitle: await chapterTitle(groupId, chapterId),
    detail,
  });
});

/** New tasks and moves between columns. Edits to details stay out of the feed. */
export const taskActivity = onDocumentWritten(`${Collections.groups}/{groupId}/${Collections.tasks}/{taskId}`, async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!after) return;

  const { groupId, taskId } = event.params;
  if (!before) {
    await record(groupId, event.id, {
      type: "task_created",
      actorId: after.createdBy,
      targetId: taskId,
      targetTitle: after.title ?? "",
      detail: null,
    });
  } else if (before.status !== after.status) {
    await record(groupId, event.id, {
      type: "task_status",
      actorId: after.updatedBy,
      targetId: taskId,
      targetTitle: after.title ?? "",
      detail: after.status,
    });
  }
});
