# CHOMS - Firebase setup

One Firebase project (**`match-choms`**) powers the **website sign-up**, the **web app** and the **Android app**.
Everyone signs in with the same email + password and sees the same data.

```
                    +--------------------------------------+
 Website  -------->  |  Firebase project  match-choms       |
 (web/index.html)    |  - Authentication (email/password)   |
                     |  - Cloud Firestore (africa-south1)   |
 Web / desktop app   |  - Security rules (roles + audit)    |
 (web/app)   <-----> |  - Cloud Function deleteUser         |
                     |  - Hosting (optional)                |
 Android app  <----> |                                      |
 (android/)          +--------------------------------------+
```

## Roles

| Role | Who | Can do |
|---|---|---|
| `pending` | Anyone who just created an account (they choose CHW or Coordinator) | Nothing. Sign-in shows *"waiting for an administrator to approve it"*. |
| `chw` | Community Health Worker, approved by an admin | Only the beneficiaries, visits, referrals and appointments assigned to them. |
| `coordinator` | Programme Coordinator, approved by an admin | **Views** all records and the team, generates and saves reports. Cannot change records, approve reports or manage users. |
| `admin` | Administrators | Everything: **Users** (approve with a role, edit, delete), **Team & assignments** (assign beneficiaries, visits and appointments to a CHW), reports (approve), audit log. |

Records can only be assigned to an approved `chw` - the rules check this.

**Audit log.** Every operation is written to `auditLogs` together with the change itself (one batch), and the Firestore
rules **reject** any change that comes without its log entry. Logged actions: `register`, `approve`, `create`, `update`, `delete`.
Deleting a user is done by the `deleteUser` Cloud Function, which writes its own log entry.

---

## What is already done (set up with the Firebase CLI)

| Step | Status | How it was done |
|---|---|---|
| Project | `match-choms` | `.firebaserc` points to it |
| Email/Password sign-in | **Enabled** | `firebase deploy --only auth` (the `auth` block in `firebase.json`) |
| Firestore database | **Exists** in `africa-south1` | - |
| Security rules | **Deployed** | `firebase deploy --only firestore:rules` |
| Web app `choms-web` | Registered; values in `web/app/js/firebase-config.js` | `firebase apps:sdkconfig web ...` |
| Android app `com.match.choms` | Registered; **real** `android/app/google-services.json` in place | `firebase apps:sdkconfig android ... -o android/app/google-services.json` |
| Cloud Function `deleteUser` | **You deploy it** - step 1 below | needs the Blaze plan |

To redeploy everything later from this folder: `npx firebase-tools deploy` (website, rules, auth and functions).

---

## Step 1 - Deploy the `deleteUser` Cloud Function

The apps cannot delete another person's login (Firebase only allows that from a trusted server), so
**Users > Delete** calls the Cloud Function in `functions/index.js`. It:

1. checks the caller is signed in and is an `admin` (reads `users/{callerUid}`),
2. refuses to delete the caller's own account,
3. deletes the Firebase Auth login,
4. deletes `users/{uid}` and writes the `auditLogs` entry in one batch.

**1a. Upgrade to the Blaze plan** (Cloud Functions need it). Console > **Upgrade** (bottom-left) > **Blaze** > link a billing account.
The free monthly allowance (2 million function calls) is far more than CHOMS needs, so normal use costs nothing.
Set a budget alert of e.g. R50: Google Cloud Console > **Billing > Budgets & alerts**.

**1b. Deploy** (from this folder, in a terminal):

```bash
cd functions
npm install
cd ..
npx firebase-tools deploy --only functions
```

The first deploy takes a few minutes and switches on the APIs it needs (Cloud Functions, Cloud Build, Artifact Registry, Cloud Run).
If it asks about a cleanup policy for container images, answer **Yes** (keeps storage costs at zero).
When it finishes you see `Function URL (deleteUser(africa-south1))` or `+ functions[deleteUser(africa-south1)] Successful create operation`.

**1c. Check it:** Console > **Functions** lists `deleteUser` in `africa-south1`.
Then sign in as admin > **Users** > **Delete** on a test account. The person disappears from **Authentication > Users** and from
Firestore `users`, and the audit log shows a `delete` entry for `users`.

Logs if something fails: `npx firebase-tools functions:log --only deleteUser`.

> Until the function is deployed, **Delete** shows *"The user-deletion service is not available..."*. Everything else works without it.

## Step 2 - Create the first administrator (one time only)

Nobody can approve accounts until one administrator exists, and the first one has to be made by hand
(the Console is allowed to write directly; the apps are not).

1. **Authentication > Users > Add user**: your email + a strong password. **Copy the User UID**.
2. **Firestore Database > Start collection** (or **Add document**):
   * Collection ID: `users`
   * Document ID: **paste the UID** (do not auto-generate)
   * Fields (all type **string**):

| Field | Value |
|---|---|
| `name` | Your Name |
| `email` | the same email, lower-case |
| `phone` | *(empty)* |
| `role` | `admin` |
| `createdAt` | e.g. `2026-10-27T10:00:00.000Z` |

3. **Save**. You can now sign in to the web app and the Android app as administrator.

Shortcut: register normally in the app (you become `pending`), then in Firestore change your document's `role` to `admin`.

## Step 3 - Run and publish the web app + website

**On your PC** (needs Node.js):

```bash
npx http-server web -p 8080 -c-1
```

Open <http://localhost:8080/> (website) and <http://localhost:8080/app/> (web app). `localhost` is already an authorised domain.

**Online (free Firebase Hosting):**

```bash
npx firebase-tools deploy --only hosting
```

You get `https://match-choms.web.app/` (website) and `https://match-choms.web.app/app/` (web app).

## Step 4 - Test the whole loop

1. Website > **Create account** (or `/app/#signup`). You see *"Thank you - waiting for approval"*.
2. Firestore > `users`: the new document has `role: pending` and `requestedRole: chw` (or `coordinator`). `auditLogs` has a `register` entry.
3. Sign in as that person: *"waiting for an administrator to approve it"*.
4. Sign in as the **administrator**: the dashboard shows *"1 new account is waiting for your approval"*.
   Web: **Users > Approve** and pick the role. Android: dashboard **Users** > tap the person > **Approve...**.
5. The person can now sign in on the web **and** on Android. A CHW only sees their own records; a coordinator sees everything read-only.
6. Admin: **Team & assignments** (or **Assign** on any beneficiary / visit / appointment) gives work to a CHW. **Schedule visit** books a visit for a CHW.
7. Admin: **Users > Edit** to change name, phone or role (or set back to `pending` to lock someone out).
8. Admin: **Users > Delete** (needs step 1). The login and profile are gone.
9. Admin: **Audit log** shows every step above (filter by action).

## Step 5 - Build the Android APK

* **Android Studio:** open the `android` folder > wait for Gradle sync > **Build > Build APK(s)**.
  File: `android/app/build/outputs/apk/debug/app-debug.apk`.
* **GitHub Actions:** add the whole text of `android/app/google-services.json` as a repository secret named `GOOGLE_SERVICES_JSON`,
  then **Actions > Build Android APK > Run workflow**.
* To offer the APK on the website, upload it (a GitHub Release works) and set `APK_URL` in `web/assets/site-config.js`.

---

## Troubleshooting

| What you see | Cause and fix |
|---|---|
| `auth/operation-not-allowed` | Email/Password is off. Run `npx firebase-tools deploy --only auth`. |
| Sign-up says *"could not be saved"* | Old rules are live. Run `npx firebase-tools deploy --only firestore:rules`. |
| Sign in says *"no CHOMS profile"* | The `users` document ID is not the Auth UID, or `role` is not exactly `chw` / `coordinator` / `admin` (lower-case). |
| *"The user-deletion service is not available"* | The `deleteUser` function is not deployed (step 1). |
| Function deploy: *"must be on the Blaze plan"* | Do step 1a first. |
| Function deploy fails on region | Change `region` in `functions/index.js` **and** `africa-south1` in `web/app/js/firebase.js` and `Constants.FUNCTIONS_REGION` (Android) to `europe-west1`, then deploy again. |
| `permission-denied` for a CHW | Normal if the record is assigned to someone else. Otherwise check their `role` is `chw`. |
| Admin cannot save a record | The assigned person is not an approved `chw` (e.g. deleted or pending). Pick another CHW. |
| Changes stuck as "rejected" after updating the app | Changes queued offline by an older app version have no audit entry; they cannot be synced. Re-enter them. |
| Gradle: *SDK location not found* | Open the project in Android Studio once (it writes `android/local.properties`). |
