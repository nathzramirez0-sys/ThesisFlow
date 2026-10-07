# ThesisFlow

A thesis and group-project manager for college students in the Philippines. Groups track who is doing what, which chapters are done, what the adviser said, and what is due next.

> **Status:** in development. Phases 1–6 of 7 are done: accounts and groups, the chapter tracker with
> versioned drafts, tasks with a list and kanban board, adviser feedback with a group activity feed,
> push notifications, daily deadline reminders and a defense countdown, and a team stats dashboard with
> charts. All of it works offline.

## Stack

Kotlin · Jetpack Compose (Material 3) · MVVM + Clean Architecture · Hilt · Coroutines & Flow · Navigation Compose · Room · DataStore · WorkManager · Firebase Auth, Firestore, Cloud Storage, Cloud Functions and Cloud Messaging · Credential Manager (Google sign-in) · Vico charts · JUnit, MockK, Turbine

## Project layout

| Module | What it holds |
|---|---|
| `domain/` | Pure Kotlin: models, validation, repository interfaces, use cases. No Android or Firebase imports. |
| `data/` | Room cache, Firestore mapping, repositories, the sync manager that copies Firestore into Room, and the WorkManager upload worker. |
| `app/` | Compose screens, ViewModels, navigation, and the "aurora glass" design system. |
| `functions/` | TypeScript Cloud Functions: callables (`joinGroup`, `createInvite`, `deleteGroup`), clean-up triggers (`cleanUpChapter`, `cleanUpTask`, `cleanUpFeedback`), `syncMemberProfiles`, and the triggers that write the activity feed and send push notifications. |
| `firestore.rules`, `storage.rules` | Security rules: users only reach their own profile and the groups they belong to, with per-role limits (e.g. only leaders and advisers approve chapters; members move only tasks assigned to them; only advisers post feedback). The activity feed can't be written from the app at all. |

## Run it locally (no Firebase project needed)

The debug build can talk to the Firebase Local Emulator Suite, using a demo config with placeholder values.

1. Copy the demo config into place:

   ```bash
   cp app/google-services.emulator.json app/google-services.json
   ```

2. Build the functions and start the emulators (needs Node 22+ and Java 21+):

   ```bash
   npm --prefix functions install && npm --prefix functions run build
   ```

   ```bash
   npx firebase-tools emulators:start --project demo-thesisflow
   ```

3. Install the app on an Android emulator, pointed at the local emulators:

   ```bash
   ./gradlew :app:installDebug -Pthesisflow.useEmulators=true
   ```

Google sign-in and push notifications need a real project; email sign-up works against the emulators.
FCM has no emulator, so the Functions emulator logs each push and who would get it instead of sending it.
Daily reminders are worked out on the phone and do work against the emulators.

## Use a real Firebase project

1. Create a project on the **Blaze** plan (Cloud Storage needs it) and put Firestore in `asia-southeast1`.
2. Add an Android app with package `com.nathzramirez.thesisflow` and your debug SHA-1 (`./gradlew signingReport`). Enable **Email/Password** and **Google** sign-in, then download `google-services.json` into `app/`.
3. Point the CLI at the project and deploy:

   ```bash
   npx firebase-tools use --add
   ```

   ```bash
   npx firebase-tools deploy --only firestore,storage,functions,hosting
   ```

   The Firestore deploy also turns on a TTL policy (in `firestore.indexes.json`) that deletes activity entries after 180 days.

4. For invite links, set `thesisflow.inviteHost` in `gradle.properties` to your `<project-id>.web.app` domain, and put your signing certificate's SHA-256 in `hosting/public/.well-known/assetlinks.json`.

## Tests

```bash
./gradlew test
```

Fonts: [Space Grotesk](third_party/fonts/SpaceGrotesk-OFL.txt) and [Manrope](third_party/fonts/Manrope-OFL.txt), both under the SIL Open Font License.
