// storage.rules. Access comes from the group document in Firestore, so these
// tests seed Firestore (setup.js) as well as three objects in Storage: ben's
// draft and photo attachment, and the adviser's marked-up feedback file.
import { after, before, beforeEach, describe, it } from "node:test";
import { assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { deleteObject, getMetadata, listAll, ref, uploadBytes } from "firebase/storage";
import { ADVISER, GROUP, LEADER, MEMBER, OUTSIDER, seed, storageAs, testEnv } from "./setup.js";

let env;
before(async () => { env = await testEnv(); });
after(() => env.cleanup());

const DRAFT = `groups/${GROUP}/files/f1/Chapter1.pdf`;
const ATTACHMENT = `groups/${GROUP}/files/att1/photo.png`;
const FEEDBACK = `groups/${GROUP}/files/fb1/markup.pdf`;
const MB = 1024 * 1024;

/** The metadata the upload worker sets: who uploaded it, and what it's for. */
const put = (storage, path, { contentType = "application/pdf", uploadedBy, kind, bytes = 3 }) =>
  uploadBytes(ref(storage, path), new Uint8Array(bytes), { contentType, customMetadata: { uploadedBy, kind } });

/** Deletes every object. env.clearStorage() only removes the top level, and every file here is nested. */
async function clear(folder) {
  const { items, prefixes } = await listAll(folder);
  await Promise.all([...items.map((item) => deleteObject(item)), ...prefixes.map(clear)]);
}

beforeEach(async () => {
  await seed();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const storage = ctx.storage();
    await clear(ref(storage));
    await put(storage, DRAFT, { uploadedBy: MEMBER, kind: "draft" });
    await put(storage, ATTACHMENT, { contentType: "image/png", uploadedBy: MEMBER, kind: "attachment" });
    await put(storage, FEEDBACK, { uploadedBy: ADVISER, kind: "feedback" });
  });
});

/** Uploads a new file as `uid`, with the metadata the worker would set for them. */
const upload = (uid, kind, options = {}) =>
  put(storageAs(uid), `groups/${GROUP}/files/new/${options.name ?? "Chapter2.pdf"}`, { uploadedBy: uid, kind, ...options });

describe("reading", () => {
  it("everyone in the group opens every file; outsiders open none", async () => {
    for (const uid of [LEADER, MEMBER, ADVISER]) {
      await assertSucceeds(getMetadata(ref(storageAs(uid), FEEDBACK)));
    }
    await assertFails(getMetadata(ref(storageAs(OUTSIDER), DRAFT)));
  });
});

describe("uploading", () => {
  it("students upload drafts and attachments under their own name", async () => {
    await assertSucceeds(upload(MEMBER, "draft"));
    await assertSucceeds(upload(LEADER, "attachment", { name: "photo.jpg", contentType: "image/jpeg" }));
    await assertFails(upload(MEMBER, "draft", { uploadedBy: LEADER }));
  });

  it("advisers upload only feedback files, and students never do", async () => {
    await assertFails(upload(ADVISER, "draft"));
    await assertFails(upload(MEMBER, "feedback"));
    await assertSucceeds(upload(ADVISER, "feedback"));
  });

  it("outsiders can't upload into a group", async () => {
    await assertFails(upload(OUTSIDER, "draft"));
  });

  it("files are PDF, Word or images, between 1 byte and 20 MB", async () => {
    await assertFails(upload(MEMBER, "draft", { name: "notes.zip", contentType: "application/zip" }));
    await assertFails(upload(MEMBER, "draft", { bytes: 0 }));
    await assertFails(upload(MEMBER, "draft", { bytes: 20 * MB + 1 }));
    await assertSucceeds(upload(MEMBER, "draft", {
      name: "Chapter2.docx",
      contentType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    }));
  });

  it("a retried upload rewrites its own file, but nobody replaces someone else's", async () => {
    await assertSucceeds(put(storageAs(MEMBER), DRAFT, { uploadedBy: MEMBER, kind: "draft" }));
    await assertFails(put(storageAs(LEADER), DRAFT, { uploadedBy: LEADER, kind: "draft" }));
  });
});

describe("deleting", () => {
  it("drafts are version history: not even their uploader deletes them", async () => {
    await assertFails(deleteObject(ref(storageAs(MEMBER), DRAFT)));
    await assertFails(deleteObject(ref(storageAs(LEADER), DRAFT)));
  });

  it("an attachment goes with its uploader or a leader", async () => {
    await assertFails(deleteObject(ref(storageAs(ADVISER), ATTACHMENT)));
    await assertSucceeds(deleteObject(ref(storageAs(LEADER), ATTACHMENT)));
  });

  it("a feedback file goes only with its adviser", async () => {
    await assertFails(deleteObject(ref(storageAs(LEADER), FEEDBACK)));
    await assertSucceeds(deleteObject(ref(storageAs(ADVISER), FEEDBACK)));
  });
});
