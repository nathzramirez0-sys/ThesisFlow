import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { logger } from "firebase-functions/v2";
import { Collections } from "./shared";

/**
 * A push is data only, no notification block: the app picks the words (in the
 * phone's language), the channel and the screen to open, from the same fields
 * the activity feed uses. FCM data values must be strings.
 */
export type PushData = Record<string, string>;

/** FCM's answer for a token that will never work again: the app was uninstalled or the token rotated. */
const DEAD_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

/** Undelivered pushes about a review or a task go stale; FCM drops them after this. */
const PUSH_TTL_MS = 3 * 24 * 60 * 60 * 1000;

/**
 * Sends [data] to every registered device of each recipient. Each message names
 * its recipient, so a phone that has since switched accounts drops it. Tokens FCM
 * reports as dead are deleted, which keeps users/{uid}/devices from piling up.
 *
 * FCM has no emulator, so under the Functions emulator the push is logged instead.
 */
export async function pushTo(recipients: string[], data: PushData): Promise<void> {
  const uids = [...new Set(recipients)];
  if (uids.length === 0) return;
  if (process.env.FUNCTIONS_EMULATOR === "true") {
    logger.info("Push (emulator: logged, not sent)", { recipients: uids, data });
    return;
  }

  const db = getFirestore();
  await Promise.all(
    uids.map(async (uid) => {
      const devices = await db.collection(Collections.users).doc(uid).collection(Collections.devices).get();
      const targets = devices.docs
        .map((doc) => ({ ref: doc.ref, token: doc.get("token") }))
        .filter((t): t is { ref: typeof t.ref; token: string } => typeof t.token === "string");
      if (targets.length === 0) return;

      const result = await getMessaging().sendEachForMulticast({
        tokens: targets.map((t) => t.token),
        data: { ...data, recipientId: uid },
        android: { priority: "high", ttl: PUSH_TTL_MS },
      });
      await Promise.all(
        result.responses.map((response, i) =>
          !response.success && response.error && DEAD_TOKEN_CODES.has(response.error.code)
            ? targets[i].ref.delete()
            : Promise.resolve(),
        ),
      );
      if (result.failureCount > 0) logger.warn("Some pushes failed", { uid, failures: result.failureCount });
    }),
  );
}
