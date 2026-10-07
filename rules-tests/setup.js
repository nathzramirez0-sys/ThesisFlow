// Shared test fixtures: one group with a leader, a member and an adviser, plus an
// outsider, seeded with security rules off so each test starts from a known state.
import { readFileSync } from "node:fs";
import { initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { Timestamp, doc, setDoc } from "firebase/firestore";

export const GROUP = "group-1";
export const LEADER = "cara";
export const MEMBER = "ben";
export const ADVISER = "dr";
export const OUTSIDER = "eve";

let env;

/** One environment per test file; the emulator hosts come from the environment (emulators:exec sets them). */
export async function testEnv() {
  env ??= await initializeTestEnvironment({
    projectId: "demo-thesisflow",
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8") },
    storage: { rules: readFileSync(new URL("../storage.rules", import.meta.url), "utf8") },
  });
  return env;
}

/** A signed-in user's Firestore, with their email in the token like Firebase Auth gives. */
export const firestoreAs = (uid) => env.authenticatedContext(uid, { email: `${uid}@test.com` }).firestore();
export const storageAs = (uid) => env.authenticatedContext(uid, { email: `${uid}@test.com` }).storage();

export async function seed() {
  await env.clearFirestore();
  const now = Timestamp.now();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const set = (path, data) => setDoc(doc(db, path), data);
    await set(`groups/${GROUP}`, {
      name: "Team SmartBin", thesisTitle: "SmartBin", course: "BSCS", school: "UPang",
      memberIds: [LEADER, MEMBER, ADVISER],
      roles: { [LEADER]: "leader", [MEMBER]: "member", [ADVISER]: "adviser" },
      createdBy: LEADER, createdAt: now, updatedAt: now, updatedBy: LEADER,
      proposalDefenseAt: null, finalDefenseAt: null,
    });
    for (const [uid, role] of [[LEADER, "leader"], [MEMBER, "member"], [ADVISER, "adviser"]]) {
      await set(`groups/${GROUP}/members/${uid}`, { displayName: uid, photoUrl: null, role, joinedAt: now });
    }
    await set(`groups/${GROUP}/chapters/ch1`, {
      title: "Introduction", order: 1, status: "for_review", deadline: null, latestVersion: 2,
      updatedAt: now, updatedBy: MEMBER,
    });
    await set(`groups/${GROUP}/tasks/t1`, {
      groupId: GROUP, title: "Draft the survey", description: "", priority: "high", status: "todo", dueAt: null,
      chapterId: null, assigneeIds: [MEMBER], createdBy: LEADER, createdAt: now, updatedAt: now,
      updatedBy: LEADER, completedAt: null, completedBy: null,
    });
    await set(`groups/${GROUP}/tasks/t1/comments/c1`, {
      groupId: GROUP, taskId: "t1", body: "On it", authorId: LEADER, authorName: "cara", createdAt: now,
    });
    const feedback = (resolvedBy) => ({
      groupId: GROUP, chapterId: "ch1", body: "Cite 1.2", authorId: ADVISER, authorName: "dr",
      authorRole: "adviser", versionNumber: 2, resolved: resolvedBy !== null, resolvedBy,
      resolvedAt: resolvedBy ? now : null, createdAt: now, updatedAt: now, updatedBy: resolvedBy ?? ADVISER,
    });
    await set(`groups/${GROUP}/chapters/ch1/feedback/open`, feedback(null));
    await set(`groups/${GROUP}/chapters/ch1/feedback/resolved`, feedback(MEMBER));
    await set(`groups/${GROUP}/activity/a1`, {
      type: "member_joined", actorId: MEMBER, actorName: "ben", targetId: null, targetTitle: "", detail: "member",
      createdAt: now,
    });
  });
}
