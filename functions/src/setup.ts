import { initializeApp } from "firebase-admin/app";
import { setGlobalOptions } from "firebase-functions/v2";

// Imported first by index.ts, so this runs before any function is defined.
initializeApp();

// Singapore is the closest region to the Philippines. It must match
// FUNCTIONS_REGION in app/build.gradle.kts and the Firestore database location.
setGlobalOptions({ region: "asia-southeast1", maxInstances: 10 });
