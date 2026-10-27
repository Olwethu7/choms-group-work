/**
 * repo.js - Repository + offline-first sync layer.
 *
 * Pattern: Repository (all Firestore access lives here) + Cache-Aside / Outbox
 *   READ  : try Firestore -> store result in localStorage -> on failure serve the cached copy.
 *   WRITE : always appended to a localStorage "outbox" first, then flushed to Firestore.
 *           If the device is offline the outbox simply waits and is flushed when the
 *           browser fires the `online` event (or the user taps "Sync now").
 *   AUDIT: every outbox operation carries its auditLogs entry and both are committed in ONE batch,
 *   so a change can never reach the server without its audit record (firestore.rules enforce this).
 */
import { db, collection, doc, getDocs, writeBatch, query, where } from './firebase.js';

let ctx = { uid: null, role: null, name: '' };
const listeners = new Set();
const PERMANENT = ['permission-denied', 'invalid-argument', 'failed-precondition', 'already-exists', 'unauthenticated'];

/* ---------- localStorage helpers (never throw) ---------- */
const LS = {
  get(key, fallback) {
    try {
      const raw = localStorage.getItem(key);
      return raw ? JSON.parse(raw) : fallback;
    } catch (e) {
      console.warn('localStorage read failed', key, e);
      return fallback;
    }
  },
  set(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
      return true;
    } catch (e) {
      console.warn('localStorage write failed (storage full or blocked)', e);
      return false;
    }
  },
  remove(key) {
    try { localStorage.removeItem(key); } catch { /* ignore */ }
  }
};

const cacheKey = (col) => `choms:${ctx.uid}:cache:${col}`;
const outboxKey = () => `choms:${ctx.uid}:outbox`;
const failedKey = () => `choms:${ctx.uid}:failed`;
const profileKey = (uid) => `choms:profile:${uid}`;

export const setContext = (c) => { ctx = { ...ctx, ...c }; };
const usableProfile = (p) => !!p && ['chw', 'coordinator', 'admin'].includes(p.role) && p.status !== 'deactivated';
/** Offline fallback only. A cached profile is used ONLY if it was an approved, active account. */
export const cachedProfile = (uid) => { const p = LS.get(profileKey(uid), null); return usableProfile(p) ? p : null; };
/** Only approved, active profiles are cached; anything else removes the cached copy (so it can never override Firestore). */
export const cacheProfile = (uid, p) => (usableProfile(p) ? LS.set(profileKey(uid), p) : LS.remove(profileKey(uid)));
export const clearCachedProfile = (uid) => LS.remove(profileKey(uid));

/* ---------- change notification (sync badge in the UI) ---------- */
export const onSyncChange = (fn) => { listeners.add(fn); return () => listeners.delete(fn); };
const notify = () => listeners.forEach((fn) => { try { fn(); } catch (e) { console.error(e); } });
export const pendingCount = () => LS.get(outboxKey(), []).length;
export const failedOps = () => LS.get(failedKey(), []);
export const clearFailed = () => LS.remove(failedKey());

const withTimeout = (promise, ms) =>
  Promise.race([
    promise,
    new Promise((_, rej) => setTimeout(() => rej(Object.assign(new Error('Request timed out'), { code: 'timeout' })), ms))
  ]);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/* ---------- queries scoped by role (must match firestore.rules): a CHW only reads their own records ---------- */
const OWNER_FIELD = { beneficiaries: 'assignedChwId', visits: 'chwId', referrals: 'chwId', appointments: 'chwId' };

function scopedQuery(col) {
  const base = collection(db, col);
  if (ctx.role === 'chw' && OWNER_FIELD[col]) return query(base, where(OWNER_FIELD[col], '==', ctx.uid));
  return base;
}

function applyOutbox(items, col) {
  let out = items.slice();
  for (const op of LS.get(outboxKey(), [])) {
    if (op.col !== col) continue;
    const idx = out.findIndex((i) => i.id === op.id);
    if (op.op === 'delete') {
      if (idx >= 0) out.splice(idx, 1);
    } else if (idx >= 0) out[idx] = { ...out[idx], ...op.data };
    else out.push({ id: op.id, ...op.data });
  }
  // CHWs only ever see their own rows, even for optimistic local rows
  if (ctx.role === 'chw' && OWNER_FIELD[col]) out = out.filter((i) => i[OWNER_FIELD[col]] === ctx.uid);
  return out;
}

/* ---------- READ ---------- */
export async function list(col) {
  let server = null;
  if (navigator.onLine) {
    try {
      const snap = await withTimeout(getDocs(scopedQuery(col)), 12000);
      server = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      LS.set(cacheKey(col), server);
    } catch (e) {
      console.warn(`Could not fetch ${col}, falling back to cache`, e);
      if (e.code === 'permission-denied') throw e;
    }
  }
  return applyOutbox(server ?? LS.get(cacheKey(col), []), col);
}

/* ---------- WRITE (queued) ---------- */
function enqueue(op) {
  const q = LS.get(outboxKey(), []);
  q.push({ opId: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`, ts: new Date().toISOString(), ...op });
  if (!LS.set(outboxKey(), q)) throw new Error('This device is out of storage - sync your pending changes and try again.');
  notify();
}

export const newId = (col) => doc(collection(db, col)).id;

/** The auditLogs document that accompanies a write (fields required by firestore.rules). */
function auditEntry(action, col, docId, summary, who = ctx) {
  return {
    action, collection: col, docId, summary: String(summary || '').slice(0, 200),
    userId: who.uid, userName: String(who.name || '').slice(0, 80), role: who.role, at: new Date().toISOString()
  };
}

/** Delete entries use a fixed id so the rules can check that the delete was logged. */
const deleteAuditId = (col, id) => `delete-${col}-${id}`;

/**
 * Create (id = null) or update a document. Returns the document id immediately after queuing;
 * waits briefly for the sync so online users see server-confirmed data.
 */
export async function save(col, id, data, { summary = '', isNew = !id } = {}) {
  const docId = id || newId(col);
  const auditId = newId('auditLogs');
  const now = new Date().toISOString();
  const payload = { ...data, updatedAt: now, updatedBy: ctx.uid, auditId };
  if (isNew) Object.assign(payload, { createdAt: now, createdBy: ctx.uid });
  enqueue({ op: 'set', col, id: docId, data: payload, audit: { id: auditId, data: auditEntry(isNew ? 'create' : 'update', col, docId, summary) } });
  await settle();
  return docId;
}

export async function remove(col, id, { summary = '' } = {}) {
  enqueue({ op: 'delete', col, id, audit: { id: deleteAuditId(col, id), data: auditEntry('delete', col, id, summary) } });
  await settle();
}

/** One batch = the change + its audit entry. */
function commitWithAudit(op) {
  const batch = writeBatch(db);
  const ref = doc(db, op.col, op.id);
  if (op.op === 'delete') batch.delete(ref);
  else batch.set(ref, op.data, { merge: true });
  if (op.audit) batch.set(doc(db, 'auditLogs', op.audit.id), op.audit.data);
  return batch.commit();
}

/**
 * Write that must reach the server now (user-account management). Not queued.
 * action: 'approve' | 'update' | 'create' (logged in auditLogs in the same batch).
 */
export async function writeOnline(col, id, data, { action = 'update', summary = '' } = {}) {
  if (!navigator.onLine) throw Object.assign(new Error('You are offline. This action needs an internet connection.'), { code: 'offline' });
  const auditId = newId('auditLogs');
  const payload = { ...data, updatedAt: new Date().toISOString(), updatedBy: ctx.uid, auditId };
  await withTimeout(commitWithAudit({ op: 'set', col, id, data: payload, audit: { id: auditId, data: auditEntry(action, col, id, summary) } }), 12000);
}

/** Admin fallback when the deleteUser Cloud Function is not deployed: remove the profile + log it in one batch. */
export async function removeUserProfile(uid, summary) {
  if (!navigator.onLine) throw Object.assign(new Error('You are offline. This action needs an internet connection.'), { code: 'offline' });
  await withTimeout(commitWithAudit({ op: 'delete', col: 'users', id: uid, audit: { id: deleteAuditId('users', uid), data: auditEntry('delete', 'users', uid, summary) } }), 12000);
}

/**
 * Self sign-up: writes the locked 'pending' profile and its 'register' audit entry together.
 * Runs before a session context exists, so the actor is passed in.
 */
export async function registerSelf(uid, { name, email, phone, requestedRole }) {
  const auditId = newId('auditLogs');
  const batch = writeBatch(db);
  batch.set(doc(db, 'users', uid), { name, email, phone, role: 'pending', requestedRole, createdAt: new Date().toISOString(), auditId });
  batch.set(doc(db, 'auditLogs', auditId), auditEntry('register', 'users', uid, `Registered ${name} <${email}> as ${requestedRole} (pending)`, { uid, name, role: 'pending' }));
  await withTimeout(batch.commit(), 12000);
}

async function settle() {
  if (navigator.onLine) await Promise.race([flush(), sleep(6000)]);
}

/* ---------- SYNC ---------- */
let flushing = null;

export function flush() {
  if (flushing) return flushing;
  flushing = (async () => {
    let synced = 0;
    const failed = [];
    while (navigator.onLine) {
      const q = LS.get(outboxKey(), []);
      if (!q.length) break;
      const op = q[0];
      try {
        await withTimeout(commitWithAudit(op), 10000);
        synced++;
      } catch (e) {
        if (PERMANENT.includes(e.code)) {
          // Rejected by the server rules - retrying can never succeed, so park it for the user to see.
          failed.push({ col: op.col, id: op.id, code: e.code, ts: op.ts });
          LS.set(failedKey(), [...failedOps(), failed[failed.length - 1]].slice(-50));
        } else {
          console.warn('Sync paused (will retry):', e.code || e.message);
          break;
        }
      }
      LS.set(outboxKey(), LS.get(outboxKey(), []).filter((o) => o.opId !== op.opId));
      notify();
    }
    return { synced, failed, remaining: pendingCount() };
  })().finally(() => {
    flushing = null;
    notify();
  });
  return flushing;
}

/** Remove cached clinical data from this device (called on sign-out when nothing is pending). */
export function wipeLocalData() {
  if (!ctx.uid) return;
  try {
    Object.keys(localStorage).filter((k) => k.startsWith(`choms:${ctx.uid}:`)).forEach((k) => localStorage.removeItem(k));
  } catch { /* ignore */ }
}

export const cachedAt = (col) => LS.get(cacheKey(col), null) !== null;
