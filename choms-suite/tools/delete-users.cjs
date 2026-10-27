/**
 * One-off admin tool: permanently deletes specific CHOMS users (Firebase Auth login + users/{uid} document)
 * and writes an auditLogs entry for each, exactly like the deleteUser Cloud Function does.
 *
 * Needs a service-account key for project match-choms:
 *   Firebase console > Project settings > Service accounts > Generate new private key
 *
 * Run (from the choms-suite folder; uses the firebase-admin installed in functions/):
 *   node tools/delete-users.cjs path/to/serviceAccountKey.json
 *
 * Safety: only the uid/email pairs listed in TARGETS are touched; a mismatch aborts that user.
 * Delete the key file afterwards (it gives full admin access to the project).
 */
const path = require('path');
const { initializeApp, cert } = require(path.join(__dirname, '../functions/node_modules/firebase-admin/lib/app'));
const { getAuth } = require(path.join(__dirname, '../functions/node_modules/firebase-admin/lib/auth'));
const { getFirestore } = require(path.join(__dirname, '../functions/node_modules/firebase-admin/lib/firestore'));

const TARGETS = [
  { uid: '3W6CBZakGoWErjxKg1YwH6to8612', email: 'cweleolwethu@gmail.com' },   // klate
  { uid: 'gCbfRbBRhPdo0AIsudlWMZD4E2I2', email: 'cweleolwethu6@gmail.com' }   // OLWETHU LIBONGWE CWELE
];

const keyFile = process.argv[2];
if (!keyFile) { console.error('Usage: node tools/delete-users.cjs path/to/serviceAccountKey.json'); process.exit(1); }
const key = require(path.resolve(keyFile));
if (key.project_id !== 'match-choms') { console.error(`Key is for project "${key.project_id}", expected match-choms. Aborting.`); process.exit(1); }

initializeApp({ credential: cert(key), projectId: 'match-choms' });
const auth = getAuth();
const db = getFirestore();

(async () => {
  for (const t of TARGETS) {
    try {
      let authUser = null;
      try { authUser = await auth.getUser(t.uid); } catch (e) { if (e.code !== 'auth/user-not-found') throw e; }
      if (authUser && String(authUser.email).toLowerCase() !== t.email) {
        console.log(`SKIP ${t.uid}: Auth email is ${authUser.email}, expected ${t.email}`); continue;
      }
      const ref = db.doc(`users/${t.uid}`);
      const snap = await ref.get();
      const p = snap.exists ? snap.data() : null;
      if (p && p.email && String(p.email).toLowerCase() !== t.email) {
        console.log(`SKIP ${t.uid}: profile email is ${p.email}, expected ${t.email}`); continue;
      }
      if (authUser) await auth.deleteUser(t.uid);
      const batch = db.batch();
      if (p) batch.delete(ref);
      batch.set(db.collection('auditLogs').doc(), {
        action: 'delete', collection: 'users', docId: t.uid,
        summary: `Deleted user ${p?.name || ''} <${t.email}> (${p?.role || 'no profile'}) via admin script`.slice(0, 200),
        userId: 'admin-script', userName: 'Admin SDK script', role: 'admin', at: new Date().toISOString()
      });
      await batch.commit();
      console.log(`DELETED ${t.email}: Auth ${authUser ? 'removed' : 'was already gone'}, profile ${p ? 'removed' : 'was already gone'}`);
    } catch (e) {
      console.log(`FAILED ${t.email}: ${e.code || e.message}`);
    }
  }
  process.exit(0);
})();
