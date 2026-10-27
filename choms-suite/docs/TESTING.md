# Testing evidence

## 1. Automated unit tests

**Web (Jest, 48 tests)** - `web/app/tests/utils.test.js`, run with `cd web/app && npm install && npm test`.
Covers: text sanitising / XSS escaping, date + date-time validation, SA ID (Luhn, DOB, gender), phone + email + password rules,
beneficiary / visit / referral / appointment validation, the RBAC matrix (pending / user / admin), report maths, CSV escaping with formula-injection guard.

**Android (JUnit, 20 tests)** - `android/app/src/test/java/com/match/choms/ValidatorsTest.java`, run with `./gradlew testDebugUnitTest`.
Covers the same business rules in Java, plus the **self sign-up form** (empty form, weak / mismatched password, bad phone and name), **roles** (only `user` / `admin` can sign in; `pending` and old role names cannot) and **appointments** (past booking rejected, reminder due 24 h before, old appointment can still be marked attended).

**Continuous integration** - `.github/workflows/main.yml` runs the Jest suite on Node 20 and 22 for every push and pull request;
`.github/workflows/android-apk.yml` runs the JUnit tests and builds the debug APK.

**Security rules (Firestore emulator, 47 checks)** - run during development against `firebase/firestore.rules` with `@firebase/rules-unit-testing`.
Covers: pending sign-up only with its `register` audit entry (no self-promotion, no extra fields), pending users see nothing,
admin approve / edit (email and own role locked), no user deletes from the apps, user record isolation, every create / update / delete
rejected without its audit entry, forged or edited audit entries rejected, reports and audit log admin-only.

## 2. Automated UI smoke test (development check)

A headless Chromium run drove the real web-app code against an in-memory stand-in for Firebase and passed **48 / 48** checks, including:
sign-up validation, locked *pending* profile written on sign-up, pending user refused at sign-in, admin approval alert + badge,
approval with role, CHW data scoping, form validation, ID auto-fill, CRUD for all entities, offline queue + sync, coordinator / admin permissions,
reports, admin user creation and the audit trail.
Note: this smoke test was run on the earlier admin / coordinator / CHW version and has not been re-run for the pending / user / admin model.
This checks the app's logic; it does not replace the manual tests below on your real Firebase project.

## 3. Manual test cases (take screenshots as evidence)

| ID | Test | Steps | Expected | Result |
|----|------|-------|----------|--------|
| M1 | Sign-up validation | Create account with empty form, weak password, mismatched passwords | Red field messages; nothing created | |
| M2 | Sign-up | Register (web or Android) | "Waiting for approval" screen; Firestore `users` doc has `role: pending`; `auditLogs` has a `register` entry | |
| M3 | Pending cannot sign in | Sign in as the new person | "Waiting for an administrator to approve" notice | |
| M4 | Approve | Admin: Users > Approve (web or Android) | `role` becomes `user`; person can sign in; `approve` audit entry | |
| M5 | Edit / delete user | Admin edits a user's role; deletes a test user | Role changes; deleted person is gone from Authentication and `users`; audit entries written | |
| M6 | Login - wrong password | Valid email, wrong password | "Incorrect email or password." | |
| M7 | Login lockout | 5 wrong attempts | 60 s lock message | |
| M8 | Beneficiary validation | Save empty form | Field errors, focus on first field | |
| M9 | ID auto-fill / duplicate | Valid 13-digit ID; register it twice | DOB + gender fill; duplicate blocked | |
| M10 | POPIA consent | Untick consent, Save | Blocked with message | |
| M11 | User isolation | Sign in as user A on web and Android | Only user A's records | |
| M12 | Offline visit (web) | Airplane mode, record a visit, go online | Saved offline; "synced to the cloud" | |
| M13 | Cross-device | Add a beneficiary on Android, refresh web | Appears in the web app | |
| M14 | Appointment | Book, then change the time | Status becomes `rescheduled`; past dates rejected | |
| M15 | Reports | Admin saves a report, approves it, exports CSV; a user has no Reports menu | As described | |
| M16 | Rules Playground | Firestore > Rules > Playground: unauthenticated read of `beneficiaries` | Denied | |
| M17 | Responsive | Web app at 390 px wide | Bottom tab bar, tables become cards | |
| M18 | Dark mode | Switch device to dark | Website and web app switch theme | |
