# CHOMS Android app

Java, MVVM + Repository, Firebase Auth + Cloud Firestore, Material 3.

1. `app/google-services.json` is the real file for the `match-choms` project. See **FIREBASE_SETUP.md** in the folder above for the first administrator and the Cloud Function.
2. Android Studio > **File > Open** > choose this `android` folder > wait for Gradle sync (first time ~500 MB).
3. Run on an emulator or phone (USB debugging), or **Build > Build APK(s)**.
4. Sign-in screen > *Create an account* to register (role `pending`); an administrator approves you (role `user`). Admins manage accounts from the dashboard **Users** tile.

Tests: `./gradlew testDebugUnitTest`. Package name must stay `com.match.choms` (it is baked into `google-services.json`).
