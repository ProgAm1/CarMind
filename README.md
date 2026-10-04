# CarMind

Connected Car / IoT / Automotive Software graduation project.

- [Project baseline and scope](docs/PROJECT_CONTEXT.md)
- [Codex instructions](AGENTS.md)

CarMind is the team's vehicle monitoring app for CCSW 431. This first increment implements Abdullah Misar's email/password login story in native Android using Java, XML layouts, and Firebase Authentication. The authenticated home screen is a minimal Sprint 1 landing page. Vehicle monitoring features belong to later increments.

## Open and build

Open this repository folder in Android Studio. Use Android SDK 37, Build Tools 36.0.0, and JDK 17 or newer. The app supports Android 7.0 (API 24) or later. The included Gradle wrapper pins Gradle 9.6.0 and checks its distribution checksum.

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
```

On Windows, use `gradlew.bat`. Android Studio creates your machine's `local.properties`; do not commit that file.

## Antigravity editor diagnostics

The workspace enables the Java extension's experimental Android import support. The installed extension still reports some Android files as outside its classpath; this notice is not suppressed. Use the Gradle build, unit tests, and Android lint command above to validate the app, or Android Studio for Android project-aware editing. No application code was changed to work around the editor's project model.

## Connect the team Firebase project

1. Create or select the team's Firebase project.
2. Register an Android app with package name `com.carmind.app`.
3. Download its `google-services.json` to `app/google-services.json`.
4. Enable **Authentication > Sign-in method > Email/Password**.
5. Use an existing account in Firebase Console, then rebuild and run the app.

The Gradle build applies the Google Services plugin when the configuration file exists. Without it, the ordinary app builds but disables login and displays an unavailable message. The configuration file is local and is not tracked in Git. `google-services.json` is ignored so the team shares that environment configuration deliberately.

Firebase owns credentials and session tokens. The app does not store passwords in a database or SharedPreferences, print credentials, or display raw Firebase exceptions. Any future backend or database must enforce authorization using verified identity and security rules. The Android home screen check alone does not authorize access to server data.

## Daily run with the team Firebase project

The app uses the Firebase project configured in `app/google-services.json`. The team's current project is `carmind-senior`, with Android package `com.carmind.app`. There is no local Auth emulator mode or automatic demo-account creation.

```sh
./scripts/dev.sh
```

This starts the Android Virtual Device if needed, builds and installs the app, then exits. Sign in with an existing account from the team's Firebase Authentication console.

## Registration integration

The teammate can add `com.carmind.app.RegistrationActivity` and declare it in `app/src/main/AndroidManifest.xml` with `android:exported="false"`. The login screen displays **Create an account** automatically when that activity resolves. Until then, it hides the unavailable action.

Registration must get the shared auth instance with:

```java
FirebaseAuth auth = ((CarMindApplication) getApplication()).getAuth();
```

Check for a null instance before submitting. Use `createUserWithEmailAndPassword` to create the account. That call also signs the user in. If the registration story requires returning to login, call `auth.signOut()`, open `LoginActivity`, and finish registration. Do not trim the password on either screen. The login story trims surrounding email whitespace and sends the password exactly as entered.

## Sprint 1 evidence

See [the Sprint 1 handoff](docs/SPRINT1.md) for the story, test cases, meeting templates and demo sequence. The presentation focuses on Abdullah's login contribution and reserves a slide for the teammate's registration evidence. Registration is not implemented in this branch.

The current deck is [CarMind Sprint 1](docs/CarMind_Sprint1_Deck.pptx). It uses the CarMind graphite/teal palette, genuine Android screenshots, editable text and presenter notes. Slide 9 reserves space for registration. The [identity and artwork notes](docs/presentation-assets/IDENTITY.txt) record the palette, font and cover source. The earlier deck remains in `docs/archive/` for reference.

## References

- [CCSW 431 course examples and project brief](https://ccsw431.malahmadi.sa/)
- [Firebase Android email/password authentication](https://firebase.google.com/docs/auth/android/password-auth)
- [Android Gradle plugin 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes)

The early senior-project CarMind report supplies the app concept. Its prototype claims are not evidence that these course features have passed tests.
