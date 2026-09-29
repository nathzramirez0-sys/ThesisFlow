# ThesisFlow

A thesis and group-project manager for college students in the Philippines. Groups track who is doing what, which chapters are done, what the adviser said, and what is due next.

> **Status:** in development. Phase 1 of 7 is done: accounts, profiles, and creating, joining and managing groups.

## Stack

Kotlin · Jetpack Compose (Material 3) · MVVM + Clean Architecture · Hilt · Coroutines & Flow · Navigation Compose · Room · Firebase Auth, Firestore and Cloud Functions · Credential Manager (Google sign-in) · JUnit, MockK, Turbine

## Project layout

| Module | What it holds |
|---|---|
| `domain/` | Pure Kotlin: models, validation, repository interfaces, use cases. No Android or Firebase imports. |
| `data/` | Room cache, Firestore mapping, repositories, and the sync manager that copies Firestore into Room. |
| `app/` | Compose screens, ViewModels, navigation, theme. |
| `functions/` | TypeScript Cloud Functions: `joinGroup`, `createInvite`, `deleteGroup`, `syncMemberProfiles`. |
| `firestore.rules` | Security rules: users only reach their own profile and the groups they belong to. |

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

Google sign-in needs a real project; email sign-up works against the emulators.

## Use a real Firebase project

1. Create a project on the **Blaze** plan (Cloud Storage needs it) and put Firestore in `asia-southeast1`.
2. Add an Android app with package `com.nathzramirez.thesisflow` and your debug SHA-1 (`./gradlew signingReport`). Enable **Email/Password** and **Google** sign-in, then download `google-services.json` into `app/`.
3. Point the CLI at the project and deploy:

   ```bash
   npx firebase-tools use --add
   ```

   ```bash
   npx firebase-tools deploy --only firestore,functions,hosting
   ```

4. For invite links, set `thesisflow.inviteHost` in `gradle.properties` to your `<project-id>.web.app` domain, and put your signing certificate's SHA-256 in `hosting/public/.well-known/assetlinks.json`.

## Tests

```bash
./gradlew test
```
