// firestore.rules, tested the way the app writes: the same fields, batches and
// server timestamps as the repositories in :data. Each test starts from the
// seed in setup.js (cara leads, ben is a member, dr advises, eve is an outsider).
import { after, before, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import {
  Timestamp, arrayRemove, collection, collectionGroup, deleteDoc, deleteField, doc, getDoc, getDocs, limit,
  orderBy, query, serverTimestamp, setDoc, updateDoc, where, writeBatch,
} from "firebase/firestore";
import { ADVISER, GROUP, LEADER, MEMBER, OUTSIDER, draftFile, firestoreAs, seed, testEnv } from "./setup.js";

let env;
before(async () => { env = await testEnv(); });
beforeEach(seed);
after(() => env.cleanup());

const inGroup = (path) => `groups/${GROUP}/${path}`;
const groupOf = (db) => doc(db, "groups", GROUP);
const MB = 1024 * 1024;

/** Every write that changes a document stamps who and when, like the repositories do. */
const stamp = (uid) => ({ updatedAt: serverTimestamp(), updatedBy: uid });

const newChapter = (title, order, uid) => ({
  title, order, status: "not_started", deadline: null, latestVersion: 0, ...stamp(uid),
});

const newTask = (uid, assigneeIds) => ({
  groupId: GROUP, title: "Pilot the survey", description: "", priority: "medium", status: "todo", dueAt: null,
  chapterId: null, assigneeIds, createdBy: uid, createdAt: serverTimestamp(), ...stamp(uid),
  completedAt: null, completedBy: null,
});

const newComment = (uid, body = "Done with the first half") => ({
  groupId: GROUP, taskId: "t1", body, authorId: uid, authorName: uid, createdAt: serverTimestamp(),
});

const newFeedback = (uid, versionNumber = 2) => ({
  groupId: GROUP, chapterId: "ch1", body: "Tighten the scope in 1.3.", authorId: uid, authorName: uid,
  authorRole: "adviser", versionNumber, resolved: false, resolvedBy: null, resolvedAt: null,
  createdAt: serverTimestamp(), ...stamp(uid),
});

/** A files record for a task attachment or a feedback file. */
const newFile = (fileId, uid, overrides = {}) => ({
  name: "photo.png", mimeType: "image/png", sizeBytes: 120_000,
  storagePath: inGroup(`files/${fileId}/photo.png`), kind: "attachment", chapterId: null,
  taskId: "t1", feedbackId: null, uploadedBy: uid, uploadedAt: serverTimestamp(), ...overrides,
});

const feedbackFile = (fileId, uid, feedbackId = "open") => newFile(fileId, uid, {
  name: "markup.pdf", mimeType: "application/pdf", storagePath: inGroup(`files/${fileId}/markup.pdf`),
  kind: "feedback", chapterId: "ch1", taskId: null, feedbackId,
});

describe("users", () => {
  const profile = (uid, overrides = {}) => ({
    email: `${uid}@test.com`, displayName: "", photoUrl: null, course: "", school: "",
    onboardingComplete: false, createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides,
  });

  it("a user creates their own profile with the email from their sign-in", async () => {
    const db = firestoreAs(MEMBER);
    await assertSucceeds(setDoc(doc(db, `users/${MEMBER}`), profile(MEMBER)));
    await assertSucceeds(getDoc(doc(db, `users/${MEMBER}`)));
  });

  it("the email must be the one they signed in with", async () => {
    await assertFails(setDoc(doc(firestoreAs(MEMBER), `users/${MEMBER}`), profile(MEMBER, { email: "x@test.com" })));
  });

  it("nobody reads or writes someone else's profile", async () => {
    await assertSucceeds(setDoc(doc(firestoreAs(MEMBER), `users/${MEMBER}`), profile(MEMBER)));
    const eve = firestoreAs(OUTSIDER);
    await assertFails(getDoc(doc(eve, `users/${MEMBER}`)));
    await assertFails(updateDoc(doc(eve, `users/${MEMBER}`), { displayName: "Eve", updatedAt: serverTimestamp() }));
  });

  it("finishing onboarding needs a real name, course and school", async () => {
    const db = firestoreAs(MEMBER);
    const me = doc(db, `users/${MEMBER}`);
    await assertSucceeds(setDoc(me, profile(MEMBER)));
    const finish = (displayName) => updateDoc(me, {
      displayName, course: "BSCS", school: "UPang", onboardingComplete: true, updatedAt: serverTimestamp(),
    });
    await assertFails(finish("B"));
    await assertSucceeds(finish("Ben Cruz"));
  });

  it("the email can't be changed later", async () => {
    const me = doc(firestoreAs(MEMBER), `users/${MEMBER}`);
    await assertSucceeds(setDoc(me, profile(MEMBER)));
    await assertFails(updateDoc(me, { email: "other@test.com", updatedAt: serverTimestamp() }));
  });

  it("a push token is private to its owner", async () => {
    const device = (db) => doc(db, `users/${MEMBER}/devices/install-1`);
    const token = (platform = "android") => ({ token: "fcm-token", platform, updatedAt: serverTimestamp() });
    await assertSucceeds(setDoc(device(firestoreAs(MEMBER)), token()));
    await assertFails(setDoc(device(firestoreAs(MEMBER)), token("ios")));
    await assertFails(getDoc(device(firestoreAs(LEADER))));
    await assertFails(setDoc(device(firestoreAs(LEADER)), token()));
    await assertSucceeds(deleteDoc(device(firestoreAs(MEMBER))));
  });
});

describe("groups", () => {
  const newGroup = (uid, memberIds = [uid], roles = { [uid]: "leader" }) => ({
    name: "Team Eve", thesisTitle: "", course: "BSIT", school: "UPang", memberIds, roles,
    createdBy: uid, createdAt: serverTimestamp(), ...stamp(uid), proposalDefenseAt: null, finalDefenseAt: null,
  });

  it("creating a group writes it, the leader's entry and five chapters in one batch", async () => {
    const db = firestoreAs(OUTSIDER);
    const group = doc(collection(db, "groups"));
    const batch = writeBatch(db)
      .set(group, newGroup(OUTSIDER))
      .set(doc(db, `groups/${group.id}/members/${OUTSIDER}`), {
        displayName: "eve", photoUrl: null, role: "leader", joinedAt: serverTimestamp(),
      });
    ["Introduction", "Review of Related Literature", "Methodology", "Results", "Conclusion"].forEach((title, i) =>
      batch.set(doc(collection(db, `groups/${group.id}/chapters`)), newChapter(title, i + 1, OUTSIDER)));
    await assertSucceeds(batch.commit());
  });

  it("nobody creates a group with other people already in it", async () => {
    const db = firestoreAs(OUTSIDER);
    const group = newGroup(OUTSIDER, [OUTSIDER, MEMBER], { [OUTSIDER]: "leader", [MEMBER]: "member" });
    await assertFails(setDoc(doc(collection(db, "groups")), group));
  });

  it("members read their group and outsiders don't", async () => {
    await assertSucceeds(getDoc(groupOf(firestoreAs(ADVISER))));
    await assertFails(getDoc(groupOf(firestoreAs(OUTSIDER))));
  });

  it("outsiders can't let themselves in; joining goes through the function", async () => {
    const eve = firestoreAs(OUTSIDER);
    await assertFails(updateDoc(groupOf(eve), {
      memberIds: [LEADER, MEMBER, ADVISER, OUTSIDER], [`roles.${OUTSIDER}`]: "member", ...stamp(OUTSIDER),
    }));
    await assertFails(setDoc(doc(eve, inGroup(`members/${OUTSIDER}`)), {
      displayName: "eve", photoUrl: null, role: "leader", joinedAt: serverTimestamp(),
    }));
  });

  it("even a leader can't add members directly", async () => {
    await assertFails(updateDoc(groupOf(firestoreAs(LEADER)), {
      memberIds: [LEADER, MEMBER, ADVISER, OUTSIDER], [`roles.${OUTSIDER}`]: "member", ...stamp(LEADER),
    }));
  });

  it("only leaders edit the details, and every edit names its editor", async () => {
    await assertSucceeds(updateDoc(groupOf(firestoreAs(LEADER)), { name: "Team SmartBin 2", ...stamp(LEADER) }));
    await assertFails(updateDoc(groupOf(firestoreAs(LEADER)), { name: "Team SmartBin 3", ...stamp(MEMBER) }));
    await assertFails(updateDoc(groupOf(firestoreAs(MEMBER)), { name: "Ben's group", ...stamp(MEMBER) }));
  });

  it("defense dates are timestamps or empty", async () => {
    const leader = groupOf(firestoreAs(LEADER));
    const proposal = Timestamp.fromDate(new Date("2026-11-20T09:00:00+08:00"));
    await assertSucceeds(updateDoc(leader, { proposalDefenseAt: proposal, ...stamp(LEADER) }));
    await assertFails(updateDoc(leader, { finalDefenseAt: "next semester", ...stamp(LEADER) }));
  });

  const leave = (db, uid, ...leaving) => writeBatch(db)
    .update(groupOf(db), {
      memberIds: arrayRemove(...leaving),
      ...Object.fromEntries(leaving.map((it) => [`roles.${it}`, deleteField()])),
      ...stamp(uid),
    })
    .delete(doc(db, inGroup(`members/${leaving[0]}`)))
    .commit();

  it("a member can leave, taking only themselves", async () => {
    await assertFails(leave(firestoreAs(MEMBER), MEMBER, MEMBER, ADVISER));
    await assertSucceeds(leave(firestoreAs(MEMBER), MEMBER, MEMBER));
  });

  it("the last leader can't leave", async () => {
    await assertFails(leave(firestoreAs(LEADER), LEADER, LEADER));
  });

  it("a leader removes members and changes roles, keeping the member entry in step", async () => {
    const db = firestoreAs(LEADER);
    const changeRole = (groupRole, entryRole) => writeBatch(db)
      .update(groupOf(db), { [`roles.${MEMBER}`]: groupRole, ...stamp(LEADER) })
      .update(doc(db, inGroup(`members/${MEMBER}`)), { role: entryRole })
      .commit();
    await assertFails(changeRole("leader", "adviser"));
    await assertSucceeds(changeRole("leader", "leader"));
    await assertSucceeds(leave(db, LEADER, ADVISER));
  });

  it("members can't change roles", async () => {
    await assertFails(updateDoc(groupOf(firestoreAs(MEMBER)), {
      [`roles.${MEMBER}`]: "leader", ...stamp(MEMBER),
    }));
  });

  it("groups are deleted only through the function", async () => {
    await assertFails(deleteDoc(groupOf(firestoreAs(LEADER))));
  });

  it("only the leader sees invite codes, and nobody writes them from the app", async () => {
    await assertSucceeds(getDoc(doc(firestoreAs(LEADER), inGroup("invites/member"))));
    await assertFails(getDoc(doc(firestoreAs(MEMBER), inGroup("invites/member"))));
    await assertFails(setDoc(doc(firestoreAs(LEADER), inGroup("invites/member")), { code: "ABC123" }));
    await assertFails(getDoc(doc(firestoreAs(LEADER), "inviteCodes/ABC123")));
    await assertFails(setDoc(doc(firestoreAs(LEADER), "inviteCodes/ABC123"), { groupId: GROUP }));
  });
});

describe("chapters and drafts", () => {
  const chapter = (db) => doc(db, inGroup("chapters/ch1"));

  it("leaders add and delete chapters; members can't", async () => {
    await assertSucceeds(setDoc(doc(firestoreAs(LEADER), inGroup("chapters/ch6")), newChapter("Appendix", 6, LEADER)));
    await assertFails(setDoc(doc(firestoreAs(MEMBER), inGroup("chapters/ch7")), newChapter("Notes", 7, MEMBER)));
    await assertFails(deleteDoc(chapter(firestoreAs(MEMBER))));
    await assertSucceeds(deleteDoc(chapter(firestoreAs(LEADER))));
  });

  it("members move a chapter along but only leaders and advisers approve it", async () => {
    await assertSucceeds(updateDoc(chapter(firestoreAs(MEMBER)), { status: "drafting", ...stamp(MEMBER) }));
    await assertFails(updateDoc(chapter(firestoreAs(MEMBER)), { status: "approved", ...stamp(MEMBER) }));
    await assertSucceeds(updateDoc(chapter(firestoreAs(ADVISER)), { status: "approved", ...stamp(ADVISER) }));
    // ...and a member can't undo the approval either.
    await assertFails(updateDoc(chapter(firestoreAs(MEMBER)), { status: "revisions", ...stamp(MEMBER) }));
  });

  it("advisers and members can't rename chapters or move deadlines", async () => {
    await assertFails(updateDoc(chapter(firestoreAs(ADVISER)), { title: "Intro", ...stamp(ADVISER) }));
    await assertFails(updateDoc(chapter(firestoreAs(MEMBER)), { deadline: Timestamp.now(), ...stamp(MEMBER) }));
    await assertSucceeds(updateDoc(chapter(firestoreAs(LEADER)), { title: "Intro", ...stamp(LEADER) }));
  });

  // The upload worker does this in a transaction; the rules see the same three writes.
  const uploadDraft = (uid, versionNumber, fileId = "f3") => {
    const db = firestoreAs(uid);
    return writeBatch(db)
      .set(doc(db, inGroup(`files/${fileId}`)), draftFile(fileId, uid, serverTimestamp()))
      .set(doc(db, inGroup(`chapters/ch1/versions/${versionNumber}`)), {
        groupId: GROUP, chapterId: "ch1", versionNumber, fileId, note: "Added the scope",
        uploadedBy: uid, uploadedAt: serverTimestamp(),
      })
      .update(chapter(db), { latestVersion: versionNumber, ...stamp(uid) })
      .commit();
  };

  it("a new draft is the next version, written with its file record", async () => {
    await assertFails(uploadDraft(MEMBER, 4)); // skips v3
    await assertSucceeds(uploadDraft(MEMBER, 3));
  });

  it("a version can't be added without bumping the chapter", async () => {
    const db = firestoreAs(MEMBER);
    await assertFails(setDoc(doc(db, inGroup("chapters/ch1/versions/3")), {
      groupId: GROUP, chapterId: "ch1", versionNumber: 3, fileId: "f2", note: "",
      uploadedBy: MEMBER, uploadedAt: serverTimestamp(),
    }));
  });

  it("advisers don't upload drafts", async () => {
    await assertFails(uploadDraft(ADVISER, 3));
  });

  it("version history can't be rewritten or deleted", async () => {
    const version = doc(firestoreAs(LEADER), inGroup("chapters/ch1/versions/1"));
    await assertFails(updateDoc(version, { note: "rewritten" }));
    await assertFails(deleteDoc(version));
    await assertFails(deleteDoc(doc(firestoreAs(MEMBER), inGroup("files/f1"))));
  });
});

describe("tasks and comments", () => {
  const task = (db) => doc(db, inGroup("tasks/t1"));

  it("leaders create tasks for group members only", async () => {
    const db = firestoreAs(LEADER);
    await assertSucceeds(setDoc(doc(db, inGroup("tasks/t2")), newTask(LEADER, [MEMBER, LEADER])));
    await assertFails(setDoc(doc(db, inGroup("tasks/t3")), newTask(LEADER, [OUTSIDER])));
    await assertFails(setDoc(doc(firestoreAs(MEMBER), inGroup("tasks/t4")), newTask(MEMBER, [MEMBER])));
  });

  it("an assignee finishes their task under their own name", async () => {
    const db = firestoreAs(MEMBER);
    const done = (completedBy) => updateDoc(task(db), {
      status: "done", completedAt: serverTimestamp(), completedBy, ...stamp(MEMBER),
    });
    await assertFails(updateDoc(task(db), { status: "done", ...stamp(MEMBER) })); // no completion fields
    await assertFails(done(LEADER));
    await assertSucceeds(done(MEMBER));
  });

  it("assignees only move a task; editing it is the leader's", async () => {
    await assertFails(updateDoc(task(firestoreAs(MEMBER)), { title: "Skip the survey", ...stamp(MEMBER) }));
    await assertFails(updateDoc(task(firestoreAs(ADVISER)), { status: "in_progress", ...stamp(ADVISER) }));
    await assertSucceeds(updateDoc(task(firestoreAs(LEADER)), { assigneeIds: [LEADER], ...stamp(LEADER) }));
    // Unassigned now, so ben can't move it any more.
    await assertFails(updateDoc(task(firestoreAs(MEMBER)), { status: "in_progress", ...stamp(MEMBER) }));
  });

  it("only leaders delete tasks", async () => {
    await assertFails(deleteDoc(task(firestoreAs(MEMBER))));
    await assertSucceeds(deleteDoc(task(firestoreAs(LEADER))));
  });

  it("everyone in the group comments, the adviser included, under their own name", async () => {
    await assertSucceeds(setDoc(doc(firestoreAs(ADVISER), inGroup("tasks/t1/comments/c2")), newComment(ADVISER)));
    await assertFails(setDoc(doc(firestoreAs(MEMBER), inGroup("tasks/t1/comments/c3")), newComment(LEADER)));
    await assertFails(setDoc(doc(firestoreAs(OUTSIDER), inGroup("tasks/t1/comments/c4")), newComment(OUTSIDER)));
  });

  it("comments are never edited; authors take theirs back and leaders moderate", async () => {
    const ben = firestoreAs(MEMBER);
    await assertSucceeds(setDoc(doc(ben, inGroup("tasks/t1/comments/c2")), newComment(MEMBER)));
    await assertFails(updateDoc(doc(ben, inGroup("tasks/t1/comments/c2")), { body: "Edited" }));
    await assertFails(deleteDoc(doc(ben, inGroup("tasks/t1/comments/c1")))); // cara's
    await assertSucceeds(deleteDoc(doc(firestoreAs(LEADER), inGroup("tasks/t1/comments/c2"))));
  });
});

describe("files", () => {
  const file = (uid, fileId) => doc(firestoreAs(uid), inGroup(`files/${fileId}`));

  it("students attach files to tasks; advisers attach files only to their own feedback", async () => {
    await assertSucceeds(setDoc(file(MEMBER, "att1"), newFile("att1", MEMBER)));
    await assertFails(setDoc(file(ADVISER, "att2"), newFile("att2", ADVISER)));
    await assertFails(setDoc(file(MEMBER, "fb1"), feedbackFile("fb1", MEMBER)));
    await assertSucceeds(setDoc(file(ADVISER, "fb2"), feedbackFile("fb2", ADVISER)));
  });

  it("a feedback file must belong to feedback the adviser wrote", async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), inGroup("chapters/ch1/feedback/other")), newFeedback("dr2")));
    await assertFails(setDoc(file(ADVISER, "fb3"), feedbackFile("fb3", ADVISER, "other")));
  });

  it("the record must point at its own path, under 20 MB, in a supported format", async () => {
    await assertFails(setDoc(file(MEMBER, "att2"), newFile("att2", MEMBER, { storagePath: inGroup("files/att1/photo.png") })));
    await assertFails(setDoc(file(MEMBER, "att3"), newFile("att3", MEMBER, { sizeBytes: 21 * MB })));
    await assertFails(setDoc(file(MEMBER, "att4"), newFile("att4", MEMBER, { mimeType: "application/zip" })));
    await assertFails(setDoc(file(MEMBER, "att5"), newFile("att5", MEMBER, { uploadedBy: LEADER })));
  });

  it("an attachment goes with its uploader or a leader; a feedback file only with its adviser", async () => {
    await assertSucceeds(setDoc(file(MEMBER, "att1"), newFile("att1", MEMBER)));
    await assertSucceeds(setDoc(file(ADVISER, "fb1"), feedbackFile("fb1", ADVISER)));
    await assertFails(deleteDoc(file(LEADER, "fb1")));
    await assertSucceeds(deleteDoc(file(ADVISER, "fb1")));
    await assertSucceeds(deleteDoc(file(LEADER, "att1")));
  });

  it("records are never edited", async () => {
    await assertFails(updateDoc(file(MEMBER, "f1"), { name: "Final.pdf" }));
  });
});

describe("adviser feedback", () => {
  const item = (uid, id) => doc(firestoreAs(uid), inGroup(`chapters/ch1/feedback/${id}`));

  it("only advisers post, about a version that exists", async () => {
    await assertSucceeds(setDoc(item(ADVISER, "new"), newFeedback(ADVISER)));
    await assertFails(setDoc(item(ADVISER, "future"), newFeedback(ADVISER, 3)));
    await assertFails(setDoc(item(MEMBER, "student"), { ...newFeedback(MEMBER), authorRole: "adviser" }));
  });

  it("posting can send the chapter back for revisions in the same batch", async () => {
    const db = firestoreAs(ADVISER);
    await assertSucceeds(writeBatch(db)
      .set(doc(db, inGroup("chapters/ch1/feedback/new")), newFeedback(ADVISER))
      .update(doc(db, inGroup("chapters/ch1")), { status: "revisions", ...stamp(ADVISER) })
      .commit());
  });

  const resolve = (uid, resolvedBy = uid) => updateDoc(item(uid, "open"), {
    resolved: true, resolvedBy, resolvedAt: serverTimestamp(), ...stamp(uid),
  });
  const reopen = (uid) => updateDoc(item(uid, "resolved"), {
    resolved: false, resolvedBy: null, resolvedAt: null, ...stamp(uid),
  });

  it("anyone in the group resolves it, under their own name", async () => {
    await assertFails(resolve(OUTSIDER));
    await assertFails(resolve(MEMBER, LEADER));
    await assertSucceeds(resolve(MEMBER));
  });

  it("the student who resolved it can reopen it", async () => {
    await assertFails(reopen(LEADER));
    await assertSucceeds(reopen(MEMBER));
  });

  it("the adviser can reopen anything", async () => {
    await assertSucceeds(reopen(ADVISER));
  });

  it("the text can't be edited after posting", async () => {
    await assertFails(updateDoc(item(ADVISER, "open"), { body: "Never mind", ...stamp(ADVISER) }));
  });

  it("only its author deletes it", async () => {
    await assertFails(deleteDoc(item(LEADER, "open")));
    await assertSucceeds(deleteDoc(item(ADVISER, "open")));
  });
});

describe("activity feed", () => {
  it("members read the newest entries; outsiders read nothing", async () => {
    const feed = (db) => query(collection(db, inGroup("activity")), orderBy("createdAt", "desc"), limit(100));
    await assertSucceeds(getDocs(feed(firestoreAs(MEMBER))));
    await assertFails(getDocs(feed(firestoreAs(OUTSIDER))));
  });

  it("nobody writes it from the app, not even the leader", async () => {
    const db = firestoreAs(LEADER);
    await assertFails(setDoc(doc(db, inGroup("activity/forged")), {
      type: "chapter_status_changed", actorId: MEMBER, actorName: "ben", targetId: "ch1",
      targetTitle: "Introduction", detail: "approved", createdAt: serverTimestamp(),
    }));
    await assertFails(deleteDoc(doc(db, inGroup("activity/a1"))));
  });
});

describe("collection-group sync", () => {
  const everywhere = (uid, name, filtered = true) => {
    const all = collectionGroup(firestoreAs(uid), name);
    return getDocs(filtered ? query(all, where("groupId", "==", GROUP)) : all);
  };

  for (const name of ["versions", "feedback", "comments"]) {
    it(`members sync every ${name} document in their group at once`, async () => {
      await assertSucceeds(everywhere(MEMBER, name));
      await assertFails(everywhere(OUTSIDER, name));
    });
  }

  it("a query that doesn't filter on the group is refused, even for a member", async () => {
    await assertFails(everywhere(MEMBER, "feedback", false));
  });
});
