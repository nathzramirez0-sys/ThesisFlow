import { randomInt } from "node:crypto";
import { CallableRequest, FunctionsErrorCode, HttpsError } from "firebase-functions/v2/https";

/** Same names as FirestoreSchema.kt in the Android data module. */
export const Collections = {
  users: "users",
  groups: "groups",
  members: "members",
  invites: "invites",
  inviteCodes: "inviteCodes",
  chapters: "chapters",
  tasks: "tasks",
  files: "files",
} as const;

export type Role = "leader" | "member" | "adviser";

/** A thesis group plus an adviser or two; a hard cap stops a leaked code filling a group. */
export const MAX_MEMBERS = 10;

export const INVITE_TTL_MS = 7 * 24 * 60 * 60 * 1000;

/** Same alphabet as InviteCodeFormat.kt: no 0/O or 1/I, so codes survive being read aloud. */
const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const CODE_LENGTH = 6;

export function randomCode(): string {
  let code = "";
  for (let i = 0; i < CODE_LENGTH; i++) code += CODE_ALPHABET[randomInt(CODE_ALPHABET.length)];
  return code;
}

export function normalizeCode(raw: string): string {
  return raw.toUpperCase().replace(/[\s-]/g, "");
}

export function isValidCode(code: string): boolean {
  return code.length === CODE_LENGTH && [...code].every((c) => CODE_ALPHABET.includes(c));
}

/**
 * Reasons the app can explain to the user. The Android app maps these in
 * FirebaseErrors.kt; anything else is shown as a generic error.
 */
export type Reason = "INVITE_INVALID" | "INVITE_EXPIRED" | "GROUP_FULL" | "LAST_LEADER";

export function fail(code: FunctionsErrorCode, message: string, reason?: Reason): never {
  throw new HttpsError(code, message, reason ? { reason } : undefined);
}

export function requireUid(request: CallableRequest): string {
  const uid = request.auth?.uid;
  if (!uid) fail("unauthenticated", "Sign in first.");
  return uid;
}

export function requireString(value: unknown, name: string): string {
  if (typeof value !== "string" || value.length === 0) fail("invalid-argument", `${name} is required.`);
  return value;
}
