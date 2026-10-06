import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { onDocumentCreated, onDocumentUpdated, onDocumentWritten } from "firebase-functions/v2/firestore";
import { PushData, pushTo } from "./notify";
import { Collections } from "./shared";

/**
 * The group activity feed, and the pushes that go with it. Only these triggers
 * write the feed (firestore.rules denies every client write), so an entry always
 * describes a change that really reached the server. The actor comes from a field
 * the rules force to be the writer's own uid (updatedBy, uploadedBy, authorId,
 * createdBy), so it can't be forged either.
 *
 * Pushes go only to the people an event concerns, and never to whoever caused it.
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
  | "task_status"
  | "defense_scheduled";

interface ActivityEntry {
  type: ActivityType;
  actorId: string;
  /** The chapter or task the entry links to. */
  targetId: string | null;
  /** Copied now, like any log: a later rename doesn't rewrite history. */
  targetTitle: string;
  /** Depends on the type: a status, a version number, a role, or which defense. */
  detail: string | null;
}

interface GroupInfo {
  name: string;
  roles: Record<string, string>;
}

/** What a recorded entry hands to [notify]. */
interface Recorded {
  groupId: string;
  group: GroupInfo;
  entry: ActivityEntry;
  actorName: string;
}

/** A TTL policy on expireAt (firestore.indexes.json) deletes entries after this long. */
const ACTIVITY_TTL_MS = 180 * 24 * 60 * 60 * 1000;

// "as const" keeps the literal type, so event.params knows {groupId} and {chapterId}.
const chapterPath = `${Collections.groups}/{groupId}/${Collections.chapters}/{chapterId}` as const;

/**
 * Writes one entry. Triggers run at least once, so the document id is derived
 * from the event id: a retried event rewrites its entry instead of adding a twin.
 */
async function record(groupId: string, entryId: string, entry: ActivityEntry): Promise<Recorded> {
  const [group, actorName] = await Promise.all([groupInfo(groupId), nameOf(groupId, entry.actorId)]);
  await getFirestore()
    .collection(Collections.groups).doc(groupId)
    .collection(Collections.activity).doc(entryId)
    .set({
      ...entry,
      actorName,
      createdAt: FieldValue.serverTimestamp(),
      expireAt: Timestamp.fromMillis(Date.now() + ACTIVITY_TTL_MS),
    });
  return { groupId, group, entry, actorName };
}

/** Pushes [recorded] to [recipients], minus whoever caused it. [extra] adds or overrides fields. */
async function notify(recorded: Recorded, recipients: string[], extra: PushData = {}) {
  const { groupId, group, entry, actorName } = recorded;
  await pushTo(recipients.filter((uid) => uid !== entry.actorId), {
    type: entry.type,
    groupId,
    groupName: group.name,
    actorName,
    targetId: entry.targetId ?? "",
    targetTitle: entry.targetTitle,
    detail: entry.detail ?? "",
    ...extra,
  });
}

async function groupInfo(groupId: string): Promise<GroupInfo> {
  const group = await getFirestore().collection(Collections.groups).doc(groupId).get();
  return {
    name: (group.get("name") as string | undefined) ?? "",
    roles: (group.get("roles") ?? {}) as Record<string, string>,
  };
}

function withRole(group: GroupInfo, ...roles: string[]): string[] {
  return Object.entries(group.roles).filter(([, role]) => roles.includes(role)).map(([uid]) => uid);
}

const students = (group: GroupInfo) => withRole(group, "leader", "member");
const advisers = (group: GroupInfo) => withRole(group, "adviser");
const leaders = (group: GroupInfo) => withRole(group, "leader");
const everyone = (group: GroupInfo) => Object.keys(group.roles);

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

function millisOf(value: unknown): number | null {
  return value instanceof Timestamp ? value.toMillis() : null;
}

/**
 * Changes to the group itself: joins (only through joinGroup), departures
 * (leaving or being removed), and defense dates being set or moved.
 */
export const groupActivity = onDocumentUpdated(`${Collections.groups}/{groupId}`, async (event) => {
  const before = event.data?.before;
  const after = event.data?.after;
  if (!before || !after) return;
  const { groupId } = event.params;

  const beforeIds = (before.get("memberIds") ?? []) as string[];
  const afterIds = (after.get("memberIds") ?? []) as string[];
  const roles = (after.get("roles") ?? {}) as Record<string, string>;

  const joined = afterIds.filter((uid) => !beforeIds.includes(uid));
  const left = beforeIds.filter((uid) => !afterIds.includes(uid));
  const work: Promise<unknown>[] = [
    ...joined.map(async (uid) => {
      const recorded = await record(groupId, `${event.id}-${uid}`, {
        type: "member_joined", actorId: uid, targetId: null, targetTitle: "", detail: roles[uid] ?? null,
      });
      await notify(recorded, leaders(recorded.group));
    }),
    // The app says "is no longer in the group": a removal and a departure look the same here.
    ...left.map((uid) =>
      record(groupId, `${event.id}-${uid}`, {
        type: "member_left", actorId: uid, targetId: null, targetTitle: "", detail: null,
      }),
    ),
  ];

  // Leaders set defense dates, and every client group edit records updatedBy.
  const editor = after.get("updatedBy");
  for (const [field, kind] of [["proposalDefenseAt", "proposal"], ["finalDefenseAt", "final"]] as const) {
    const at = millisOf(after.get(field));
    if (at === null || at === millisOf(before.get(field)) || typeof editor !== "string") continue;
    work.push((async () => {
      const recorded = await record(groupId, `${event.id}-${kind}`, {
        type: "defense_scheduled", actorId: editor, targetId: null, targetTitle: "", detail: kind,
      });
      await notify(recorded, everyone(recorded.group), { at: String(at) });
    })());
  }
  await Promise.all(work);
});

/**
 * Chapter status changes. The first draft also moves a chapter from Not started
 * to Drafting; that upload gets its own entry, so the status change is skipped.
 * "For review" pushes to the adviser; "approved" and "revisions" to the students.
 */
export const chapterActivity = onDocumentUpdated(chapterPath, async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!before || !after || before.status === after.status) return;
  if (before.latestVersion !== after.latestVersion) return;
  const recorded = await record(event.params.groupId, event.id, {
    type: "chapter_status",
    actorId: after.updatedBy,
    targetId: event.params.chapterId,
    targetTitle: after.title ?? "",
    detail: after.status,
  });
  if (after.status === "for_review") await notify(recorded, advisers(recorded.group));
  if (after.status === "approved" || after.status === "revisions") await notify(recorded, students(recorded.group));
});

/** New drafts go in the feed only; "for review" is the moment the adviser is told. */
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

/**
 * New feedback, and feedback resolved or reopened. Deletions are left to cleanUpFeedback.
 * New or reopened feedback pushes to the students; a resolve tells the adviser who wrote it.
 */
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
  const recorded = await record(groupId, event.id, {
    type,
    actorId,
    targetId: chapterId,
    targetTitle: await chapterTitle(groupId, chapterId),
    detail,
  });
  if (type === "feedback_resolved") {
    await notify(recorded, [after.authorId]);
  } else {
    // The first lines of the comment, so the push is worth reading on its own.
    const body = typeof after.body === "string" ? after.body : "";
    await notify(recorded, students(recorded.group), { preview: body.slice(0, 140) });
  }
});

/**
 * New tasks and moves between columns go in the feed. Pushes: people newly
 * assigned (on a new task or an edit) hear about it, and leaders hear when a
 * task is done. Other edits stay quiet.
 */
export const taskActivity = onDocumentWritten(`${Collections.groups}/{groupId}/${Collections.tasks}/{taskId}`, async (event) => {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!after) return;

  const { groupId, taskId } = event.params;
  const newAssignees = ((after.assigneeIds ?? []) as string[])
    .filter((uid) => !((before?.assigneeIds ?? []) as string[]).includes(uid));

  let recorded: Recorded | null = null;
  if (!before) {
    recorded = await record(groupId, event.id, {
      type: "task_created", actorId: after.createdBy, targetId: taskId, targetTitle: after.title ?? "", detail: null,
    });
  } else if (before.status !== after.status) {
    recorded = await record(groupId, event.id, {
      type: "task_status", actorId: after.updatedBy, targetId: taskId, targetTitle: after.title ?? "", detail: after.status,
    });
    if (after.status === "done") await notify(recorded, leaders(recorded.group));
  }

  if (newAssignees.length > 0) {
    const actorId = before ? after.updatedBy : after.createdBy;
    const context = recorded ?? {
      groupId,
      group: await groupInfo(groupId),
      actorName: await nameOf(groupId, actorId),
      entry: { type: "task_created", actorId, targetId: taskId, targetTitle: after.title ?? "", detail: null },
    };
    await notify(context, newAssignees, { type: "task_assigned", detail: "" });
  }
});
