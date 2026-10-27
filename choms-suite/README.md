# CHOMS - Community Health & Outreach Management System

**Module:** Work Integrated Learning 3 (XADAD7112w), Advanced Diploma in Application Development, The Independent Institute of Education
**Client:** Maternal, Adolescent and Child Health Institute (MatCH), Durban
**Firebase project:** `match-choms`

## Team

| Name | Student number |
|---|---|
| Mhlengi Mathonsi | ST10399162 |
| Junior Sbonakaliso Madlopha | ST10377944 |
| Olwethu Libongwe Cwele | ST10344453 |

## Repository layout

```
choms-suite/
|-- web/                 Public website (index.html) and the installable PWA (web/app)
|-- android/             Native Android app (Java, MVVM + Repository)
|-- firebase/            Firestore security rules and rules tests
|-- functions/           Cloud Function (deleteUser)
|-- tools/               Admin utility scripts
|-- docs/
|   |-- report/          Final Task 2 report (Word)
|   |-- presentation/    Defence slides (PowerPoint and PDF)
|   `-- TESTING.md       Test strategy and how to run the tests
|-- .github/workflows/   CI: Jest tests and Android APK build
|-- FIREBASE_SETUP.md    Firebase setup and deployment guide
`-- firebase.json        Hosting, rules and functions configuration
```

## Run locally

Double-click `start-server.bat` (Windows), or run the command below. To publish a public link, double-click `deploy-hosting.bat` (Firebase Hosting: https://match-choms.web.app).


```
cd choms-suite/web && npx http-server -p 8123
```

Website: http://127.0.0.1:8123/index.html  |  Web app: http://127.0.0.1:8123/app/index.html

---

Child, maternal and adolescent health outreach for Durban communities.
One Firebase backend, three matching front ends with the same design:

| System | Folder | What it is |
|---|---|---|
| **Website** | `web/index.html` | Public landing page with *Create account* / *Sign in* and the app download |
| **Web / desktop app** | `web/app/` | Installable PWA (works offline). The full system for staff |
| **Android app** | `android/` | Native Java app (MVVM + Repository, Firebase Auth + Firestore) |
| **Backend** | `firebase/`, `functions/`, `firebase.json` | Firestore security rules (roles + enforced audit log), `deleteUser` Cloud Function, auth + hosting config |

**Start here: [`FIREBASE_SETUP.md`](FIREBASE_SETUP.md)** - what is already set up on the `match-choms` project, how to deploy the Cloud Function and create the first admin.

## Accounts and roles

* Anyone can **create an account** on the website, web app or Android app.
* New accounts choose **CHW** or **Coordinator**, start as **pending** and cannot sign in until an **administrator approves** them with a role. This keeps children's data safe.
* **chw** (Community Health Worker) - only the clients, visits, referrals and appointments assigned to them.
* **coordinator** - views all records and the team, generates and saves reports. Cannot change records or manage users.
* **admin** - everything: Users (approve, assign roles, edit, delete), Team & assignments (assign beneficiaries / visits / appointments to a CHW), report approval, audit log.
* **Every operation** (register, approve, create, update, delete) is written to `auditLogs` in the same batch as the change; the Firestore rules reject changes without their log entry. Deleting a user runs in the `deleteUser` Cloud Function (`functions/`), which deletes the login and profile and logs it.

## Requirements covered

| Requirement | Web app | Android |
|---|---|---|
| FR1 Register beneficiaries (SA ID check, POPIA consent, duplicate check) | yes | yes |
| FR2 / FR9 Case files and searchable history | yes | search + edit |
| FR3 Record field visits (offline-first, auto-sync) | yes | yes (online; Firestore cache for reading) |
| FR4 Admins schedule / allocate visits | yes | yes |
| FR5 Referrals with urgency and outcomes | yes | yes |
| FR6 Appointments, reschedule, cancel, reminder due time | yes | yes |
| FR7 Role-based access control (UI **and** server rules) | yes | yes |
| FR8 Reports: generate, approve (admin only), CSV / print | yes | summary screen |
| FR10 Offline sync | outbox + service worker | cached reads only (full offline queue is web-only) |
| FR11 User accounts: self sign-up (pending) + admin approve / edit / delete | yes | yes |
| FR12 Append-only audit trail, enforced by the rules | yes | writes entries |
| Android: MVVM + Repository, Firebase Auth + Firestore, validation, friendly errors | - | yes |

## Quality

* Web: `cd web/app && npm install && npm test` - 48 Jest tests; GitHub Actions runs them on every push (`.github/workflows/main.yml`).
* Android: `./gradlew testDebugUnitTest` - 20 JUnit tests (sign-up, appointment, beneficiary, visit, referral rules).
* See [`docs/TESTING.md`](docs/TESTING.md) for the test evidence table and manual test cases.

## Folder map

```
web/index.html            website            web/assets/   shared brand.css, logo, illustration
web/app/                  web + desktop app  web/app/js/   app, repo (offline), utils (business rules), firebase
android/app/src/main/java/com/match/choms/{model,repository,viewmodel,ui,util}
firebase/firestore.rules  server-side security      functions/    deleteUser Cloud Function
.github/workflows/        main.yml (Jest)  android-apk.yml (builds the APK)
```
