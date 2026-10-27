/**
 * CHOMS Cloud Functions
 *
 * deleteUser (callable) - an administrator permanently removes a person:
 *   1. checks the caller is signed in and has role 'admin' in users/{uid}
 *   2. deletes the Firebase Auth account (apps cannot do this for another person)
 *   3. deletes users/{uid} and writes the auditLogs entry in ONE batch
 *
 * Firestore rules block deletes of users/{uid} from the apps, so this function is the only way to delete a user.
 * The Admin SDK bypasses the rules, which is why the checks are repeated here.
 */
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { setGlobalOptions, logger } = require('firebase-functions/v2');
const { initializeApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getFirestore } = require('firebase-admin/firestore');

initializeApp();

// Same region as the Firestore database (Johannesburg). The apps call the function in this region.
setGlobalOptions({ region: 'africa-south1', maxInstances: 5 });

exports.deleteUser = onCall(async (request) => {
  const callerUid = request.auth?.uid;
  if (!callerUid) throw new HttpsError('unauthenticated', 'Please sign in again.');

  const uid = typeof request.data?.uid === 'string' ? request.data.uid.trim() : '';
  if (!uid || uid.length > 128 || uid.includes('/')) throw new HttpsError('invalid-argument', 'A valid user id is required.');
  if (uid === callerUid) throw new HttpsError('failed-precondition', 'You cannot delete your own account.');

  const db = getFirestore();
  const callerSnap = await db.doc(`users/${callerUid}`).get();
  const caller = callerSnap.exists ? callerSnap.data() : null;
  if (!caller || caller.role !== 'admin') throw new HttpsError('permission-denied', 'Only administrators can delete users.');

  const targetRef = db.doc(`users/${uid}`);
  const targetSnap = await targetRef.get();
  const target = targetSnap.exists ? targetSnap.data() : null;

  // 1. Auth account. "Not found" is fine: a previous attempt may already have removed it.
  let authFound = true;
  try {
    await getAuth().deleteUser(uid);
  } catch (e) {
    if (e.code === 'auth/user-not-found') authFound = false;
    else {
      logger.error('Auth delete failed', { uid, code: e.code, message: e.message });
      throw new HttpsError('internal', 'The login could not be deleted. Please try again.');
    }
  }
  if (!authFound && !target) throw new HttpsError('failed-precondition', 'That user no longer exists.');

  // 2. Profile + audit entry together.
  const who = target ? `${target.name || ''} <${target.email || ''}> (${target.role || 'unknown'})` : uid;
  const batch = db.batch();
  batch.delete(targetRef);
  batch.set(db.collection('auditLogs').doc(), {
    action: 'delete',
    collection: 'users',
    docId: uid,
    summary: `Deleted user ${who}`.slice(0, 200),
    userId: callerUid,
    userName: String(caller.name || '').slice(0, 80),
    role: 'admin',
    at: new Date().toISOString()
  });
  await batch.commit();

  logger.info('User deleted', { uid, by: callerUid });
  return { ok: true };
});
