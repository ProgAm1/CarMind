# CarMind

CarMind is the team's vehicle monitoring app for CCSW 431. This first increment implements Abdullah Misar's email/password login story in native Android using Java, XML layouts, and Firebase Authentication. The authenticated home screen is a minimal Sprint 1 landing page. Vehicle monitoring features belong to later increments.

## Open and build

Open this repository folder in Android Studio. Use Android SDK 37, Build Tools 36.0.0, and JDK 17 or newer. The app supports Android 7.0 (API 24) or later. The included Gradle wrapper pins Gradle 9.6.0 and checks its distribution checksum.

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
```

On Windows, use `gradlew.bat`. Android Studio creates your machine's `local.properties`; do not commit that file.

## Connect the team Firebase project

1. Create or select the team's Firebase project.
2. Register an Android app with package name `com.carmind.app`.
3. Download its `google-services.json` to `app/google-services.json`.
4. Enable **Authentication > Sign-in method > Email/Password**.
5. Create a test user through Firebase Console or the teammate's registration screen, then rebuild and run the app.

The Gradle build applies the Google Services plugin when the configuration file exists. Without it, the ordinary app builds but disables login and displays an unavailable message. This repository contains no real Firebase project configuration. `google-services.json` is ignored so the team shares that environment configuration deliberately.

Firebase owns credentials and session tokens. The app does not store passwords in a database or SharedPreferences, print credentials, or display raw Firebase exceptions. Any future backend or database must enforce authorization using verified identity and security rules. The Android home screen check alone does not authorize access to server data.

## Local demonstration without a Firebase project

Install the official [Firebase CLI](https://firebase.google.com/docs/cli), then start the Auth emulator from the repository folder:

```sh
firebase emulators:start --only auth --project demo-carmind
```

In a second terminal, build and install the emulator version on an Android Virtual Device:

```sh
./gradlew assembleDebug -PauthEmulator=true
adb install -r app/build/outputs/apk/debug/app-debug.apk
python scripts/check_login.py --adb adb --device emulator-5554
```

The check script creates local demo users and exercises the installed app. The app connects to `10.0.2.2:9099`, which addresses the development computer from an Android Virtual Device. Emulator mode uses a separate named Firebase app for the `demo-carmind` project. It never uses a live Firebase account. Release builds always disable emulator mode and prohibit cleartext traffic. Debug HTTP access is limited to loopback and the Android emulator host.

Emulator mode requires an Android Virtual Device, not a physical phone. The local test passwords are disposable demo credentials, not real accounts.

## Registration integration

The teammate can add `com.carmind.app.RegistrationActivity` and declare it in `app/src/main/AndroidManifest.xml` with `android:exported="false"`. The login screen displays **Create an account** automatically when that activity resolves. Until then, it hides the unavailable action.

Registration must get the shared auth instance with:

```java
FirebaseAuth auth = ((CarMindApplication) getApplication()).getAuth();
```

Check for a null instance before submitting. Use `createUserWithEmailAndPassword` to create the account. That call also signs the user in. If the registration story requires returning to login, call `auth.signOut()`, open `LoginActivity`, and finish registration. Do not trim the password on either screen. The login story trims surrounding email whitespace and sends the password exactly as entered.

## Sprint 1 evidence

See [the Sprint 1 handoff](docs/SPRINT1.md) for the story, test cases, meeting templates and demo sequence. The presentation focuses on Abdullah's login contribution and reserves a slide for the teammate's registration evidence. Registration is not implemented in this branch.

## References

- [CCSW 431 course examples and project brief](https://ccsw431.malahmadi.sa/)
- [Firebase Android email/password authentication](https://firebase.google.com/docs/auth/android/password-auth)
- [Firebase Auth emulator](https://firebase.google.com/docs/emulator-suite/connect_auth)
- [Android Gradle plugin 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes)

The early senior-project CarMind report supplies the app concept. Its prototype claims are not evidence that these course features have passed tests.
