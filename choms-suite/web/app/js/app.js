/**
 * app.js - application controller: authentication, routing, views and event handling.
 * (Presentation layer; talks to repo.js for data and utils.js for business rules.)
 */
import {
  configured, auth, db, onAuthStateChanged, signInWithEmailAndPassword, signOut, sendPasswordResetEmail,
  createUserWithEmailAndPassword, deleteUser, doc, getDocFromServer, createAuthUser, deleteUserAccount, isFunctionMissing
} from './firebase.js';
import * as repo from './repo.js';
import { buildEntities } from './entities.js';
import { $, $$, toast, openModal, confirmDialog, formHTML, readForm, showFormErrors, badge, friendlyError, downloadFile } from './ui.js';
import {
  can, escapeHtml as esc, todayISO, addDays, formatDate, formatDateTime, calcAge, maskId, generateRef, isValidEmail,
  validatePassword, sanitizeText, summariseActivity, toCSV, matchesSearch, ROLES, ACTIVE_ROLES, SIGNUP_ROLES, normalisePhone, isFutureDateTime
} from './utils.js';

/* ------------------------------------------------------------------ */
/* State                                                               */
/* ------------------------------------------------------------------ */
const S = {
  uid: null, role: null, profile: null, users: [],
  data: { beneficiaries: [], visits: [], referrals: [], appointments: [], reports: [], auditLogs: [] },
  route: 'dashboard', search: {}, filter: {}, report: { from: '', to: '', summary: null }
};
let ENT = buildEntities(S);
const isAdmin = () => S.role === 'admin';
/** Admins and coordinators see everyone's records; a CHW only their own. */
const isStaff = () => S.role === 'admin' || S.role === 'coordinator';
const ROLE_LABEL = { pending: 'Pending approval', chw: 'Community Health Worker', coordinator: 'Programme Coordinator', admin: 'Administrator' };

const ICONS = {
  dashboard: '<path d="M3 11l9-8 9 8v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z"/>',
  beneficiaries: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6"/><path d="M16 4.5a3.5 3.5 0 0 1 0 7M18 14.2c2.2.6 3.5 2.5 3.5 5.8"/>',
  visits: '<path d="M9 4h6v3H9z"/><path d="M7 5H5a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1V6a1 1 0 0 0-1-1h-2"/><path d="M8 12h8M8 16h5"/>',
  referrals: '<path d="M5 12h14M13 6l6 6-6 6"/>',
  appointments: '<rect x="3.5" y="5" width="17" height="16" rx="2"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
  reports: '<path d="M4 20V4M4 20h16"/><path d="M8 16v-5M12 16V8M16 16v-8"/>',
  users: '<path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z"/><path d="M9 12l2 2 4-4"/>',
  team: '<circle cx="12" cy="7" r="3"/><circle cx="5" cy="17" r="2.5"/><circle cx="19" cy="17" r="2.5"/><path d="M12 10v3M12 13l-6 2.5M12 13l6 2.5"/>',
  audit: '<path d="M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01"/>'
};
const icon = (n) => `<svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONS[n] || ''}</svg>`;

const NAV = [
  { id: 'dashboard', label: 'Dashboard', roles: ACTIVE_ROLES },
  { id: 'beneficiaries', label: () => ENT.beneficiaries.title, roles: ACTIVE_ROLES },
  { id: 'visits', label: () => (isStaff() ? 'Visits' : 'My Visits'), roles: ACTIVE_ROLES },
  { id: 'referrals', label: 'Referrals', roles: ACTIVE_ROLES },
  { id: 'appointments', label: 'Appointments', roles: ACTIVE_ROLES },
  { id: 'reports', label: 'Reports', roles: ['admin', 'coordinator'] },
  { id: 'users', label: 'Users', roles: ['admin'] },
  { id: 'audit', label: 'Audit Log', roles: ['admin'] },
  // not in the sidebar: opened from the dashboard's Administration panel
  { id: 'team', label: 'Team & assignments', roles: ['admin'], hidden: true }
];
const routes = () => NAV.filter((n) => n.roles.includes(S.role));
const navItems = () => routes().filter((n) => !n.hidden);
const DASH_TITLE = { admin: 'Admin Dashboard', coordinator: 'Coordinator Dashboard', chw: 'My Dashboard' };
const navLabel = (n) => (typeof n.label === 'function' ? n.label() : n.label);

/* ------------------------------------------------------------------ */
/* Sign in / create account screen                                     */
/* ------------------------------------------------------------------ */
let signupInProgress = false;
let signupDone = false;

let authMessage = null;   // { text, type } shown on the sign-in screen; survives the sign-out that follows a refused login

/** "Waiting for approval" screen (after sign-up, or when a pending account tries to sign in). */
function showWaiting(title, text) {
  setAuthMode('done');
  $('#done-title').textContent = title;
  $('#done-text').textContent = text;
}

function setAuthMode(mode) {
  const signup = mode === 'signup';
  signupDone = mode === 'done';
  $('#login-form').hidden = signup || signupDone;
  $('#signup-form').hidden = !signup;
  $('#signup-done').hidden = !signupDone;
  $('.tabs').hidden = signupDone;
  $('#tab-login').setAttribute('aria-selected', String(!signup));
  $('#tab-signup').setAttribute('aria-selected', String(signup));
  if (!signupDone) authNotice(authMessage?.text || '', authMessage?.type);
  document.title = signup ? 'Create account | CHOMS' : 'Sign in | CHOMS';
}
const applyAuthHash = () => setAuthMode(location.hash === '#signup' ? 'signup' : 'login');

function authNotice(msg, type = 'info') {
  const n = $('#notice');
  n.hidden = !msg;
  n.className = `notice notice-${type}`;
  n.textContent = msg || '';
}

function wireAuthChrome() {
  $('#tab-login').addEventListener('click', () => { location.hash = '#login'; applyAuthHash(); });
  $('#tab-signup').addEventListener('click', () => { location.hash = '#signup'; applyAuthHash(); });
  $('#back-to-login').addEventListener('click', () => { authMessage = null; signupDone = false; location.hash = '#login'; applyAuthHash(); });
  applyAuthHash();
}

function updateStrength(pw) {
  const checks = [pw.length >= 8, /[a-z]/.test(pw), /[A-Z]/.test(pw), /\d/.test(pw), pw.length >= 12 || /[^A-Za-z0-9]/.test(pw)];
  const score = pw ? checks.filter(Boolean).length : 0;
  const bar = $('#su-meter');
  bar.style.width = `${score * 20}%`;
  bar.style.background = score <= 2 ? 'var(--coral)' : score <= 4 ? 'var(--sun)' : 'var(--leaf)';
  $('#su-strength').textContent = !pw ? 'At least 8 characters with upper-case, lower-case and a number.' : score <= 2 ? 'Weak: keep going.' : score <= 4 ? 'Almost there.' : 'Strong password.';
}

function wireSignup() {
  const form = $('#signup-form');
  const KEYS = ['name', 'email', 'phone', 'role', 'password', 'confirm', 'consent'];
  const setErrs = (errs) => {
    KEYS.forEach((k) => { const el = $(`#su-err-${k}`); if (el) el.textContent = errs[k] || ''; });
    const first = KEYS.find((k) => errs[k]);
    if (first) form.elements[first].focus();
  };
  $('#su-password').addEventListener('input', (e) => updateStrength(e.target.value));
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    $('#su-error').textContent = '';
    const f = form.elements;
    const name = sanitizeText(f.name.value, 80);
    const email = f.email.value.trim().toLowerCase();
    const phoneRaw = f.phone.value.trim();
    const pw = f.password.value;
    const requestedRole = f.role.value;
    const errs = {};
    if (name.length < 3 || !/^[\p{L}][\p{L}\s'.-]*$/u.test(name)) errs.name = 'Enter your full name (letters only).';
    if (!isValidEmail(email)) errs.email = 'Enter a valid email address.';
    let phone = '';
    if (phoneRaw) { phone = normalisePhone(phoneRaw) || ''; if (!phone) errs.phone = 'Enter a valid South African number, e.g. 082 123 4567.'; }
    if (!SIGNUP_ROLES.includes(requestedRole)) errs.role = 'Choose Community Health Worker or Programme Coordinator.';
    const pe = validatePassword(pw);
    if (pe) errs.password = pe;
    if (pw !== f.confirm.value) errs.confirm = 'The passwords do not match.';
    if (!f.consent.checked) errs.consent = 'Please tick the box to continue.';
    setErrs(errs);
    if (Object.keys(errs).length) return;

    const btn = $('#su-submit');
    btn.disabled = true;
    btn.textContent = 'Creating account...';
    signupInProgress = true;
    let created = null;
    try {
      const cred = await createUserWithEmailAndPassword(auth, email, pw);
      created = cred.user;
      // Every new account starts locked as 'pending' (logged as 'register'). An administrator approves it with a role.
      await repo.registerSelf(created.uid, { name, email, phone, requestedRole });
      await signOut(auth);
      form.reset();
      updateStrength('');
      showWaiting('Request sent', 'Your account has been created and is waiting for an administrator to approve it. You will be able to sign in as soon as that is done.');
    } catch (ex) {
      console.error(ex);
      if (created) { try { await deleteUser(created); } catch { try { await signOut(auth); } catch { /* ignore */ } } } // allow a clean retry
      $('#su-error').textContent = ex.code === 'permission-denied' ? 'Your registration could not be saved. Please try again or contact your administrator.' : friendlyError(ex);
    } finally {
      signupInProgress = false;
      btn.disabled = false;
      btn.textContent = 'Create account';
    }
  });
}

/* ---------- admin: approve / delete new accounts ---------- */
const pendingUsers = () => S.users.filter((u) => u.role === 'pending');

function updateNavBadges() {
  const n = isAdmin() ? pendingUsers().length : 0;
  $$('[data-pending]').forEach((el) => { el.hidden = !n; el.textContent = n; });
}

function pendingBanner() {
  const n = isAdmin() ? pendingUsers().length : 0;
  return n ? `<div class="alert"><span>${n} new account${n === 1 ? ' is' : 's are'} waiting for your approval.</span><a class="btn btn-sm btn-primary" href="#/users">Review</a></div>` : '';
}

const userBadge = (u) => badge(u.role === 'admin' ? 'active' : u.role === 'coordinator' ? 'booked' : u.role === 'chw' ? 'completed' : 'pending').replace(/>[^<]*</, `>${esc(ROLE_LABEL[u.role] || u.role)}<`);

function pendingCardHTML() {
  const list = pendingUsers();
  if (!list.length) return '';
  return `<section class="card"><h3>Awaiting approval (${list.length})</h3>${list.map((u) => `<div class="pending-card">
    <div><strong>${esc(u.name)}</strong><small>${esc(u.email)}${u.phone ? ` · ${esc(u.phone)}` : ''}</small>
    <small>Asked to be: ${esc(ROLE_LABEL[u.requestedRole] || 'not stated')} · registered ${formatDate(String(u.createdAt || '').slice(0, 10))}</small></div>
    <div class="row-actions"><button class="btn btn-sm btn-primary" data-action="approve-user" data-id="${u.id}">Approve</button><button class="btn btn-sm" data-action="edit-user" data-id="${u.id}">Edit</button><button class="btn btn-sm btn-danger-ghost" data-action="delete-user" data-id="${u.id}">Delete</button></div></div>`).join('')}</section>`;
}

/** Approval: the admin confirms (or changes) the role the person asked for: chw, coordinator or admin. */
function approveUser(id) {
  const u = S.users.find((x) => x.id === id);
  if (!u) return;
  const fields = [{ name: 'role', label: 'Role to give this person', type: 'select', required: true, options: ACTIVE_ROLES.map((r) => ({ value: r, label: ROLE_LABEL[r] })) }];
  const m = openModal({
    title: `Approve ${u.name}`,
    body: `<p class="muted" style="margin-bottom:1rem">${esc(u.email)} asked to be a ${esc(ROLE_LABEL[u.requestedRole] || 'CHW')}. Approving lets them sign in.</p>
      <form id="approve-form" novalidate>${formHTML(fields, { role: SIGNUP_ROLES.includes(u.requestedRole) ? u.requestedRole : 'chw' })}<div class="form-error" id="form-error" role="alert"></div></form>`,
    footer: '<button type="button" class="btn" data-close>Cancel</button><button type="submit" class="btn btn-primary" form="approve-form" id="save-btn">Approve</button>'
  });
  $('#approve-form', m.el).addEventListener('submit', async (e) => {
    e.preventDefault();
    const role = e.target.elements.role.value;
    if (!ACTIVE_ROLES.includes(role)) { showFormErrors(e.target, { role: 'Select a role.' }); return; }
    const btn = $('#save-btn', m.el);
    btn.disabled = true;
    try {
      await repo.writeOnline('users', id, { role, status: 'active', approvedBy: S.profile.name, approvedAt: new Date().toISOString() }, { action: 'approve', summary: `Approved ${u.name} as ${role}` });
      m.close();
      toast(`${u.name} can now sign in as ${ROLE_LABEL[role]}.`, 'success');
      renderUsers();
    } catch (ex) {
      $('#form-error', m.el).textContent = friendlyError(ex);
      btn.disabled = false;
    }
  });
}

/** Permanently removes the login and the profile (Cloud Function, which also writes the audit entry). */
async function deleteUserFlow(id) {
  const u = S.users.find((x) => x.id === id);
  if (!u || id === S.uid) return;
  if (!(await confirmDialog(`Delete ${u.name} (${u.email})? Their login and profile are removed permanently. Records they captured are kept.`, { confirmText: 'Delete user', danger: true }))) return;
  if (!navigator.onLine) { toast('You are offline. Deleting a user needs an internet connection.', 'error'); return; }
  try {
    await deleteUserAccount(id);   // Cloud Function: login + profile + audit entry
    toast(`${u.name} was deleted.`, 'success');
  } catch (ex) {
    if (!isFunctionMissing(ex)) { console.error(ex); toast(friendlyError(ex), 'error', 8000); return; }
    // No Cloud Function (Spark plan): remove the profile here. The person disappears from CHOMS and cannot use it;
    // their Firebase Auth login stays until it is deleted in the Firebase console.
    try {
      await repo.removeUserProfile(id, `Deleted user ${u.name} <${u.email}> (${u.role}); profile only - Auth login kept`);
      toast(`${u.name} was removed from CHOMS. Their sign-in login still exists in Firebase Authentication - delete it there to remove it completely.`, 'success', 9000);
    } catch (ex2) { console.error(ex2); toast(friendlyError(ex2), 'error', 8000); return; }
  }
  renderUsers();
}

/* ------------------------------------------------------------------ */
/* Bootstrap                                                           */
/* ------------------------------------------------------------------ */
function init() {
  window.addEventListener('unhandledrejection', (e) => { console.error(e.reason); toast(friendlyError(e.reason), 'error'); });
  window.addEventListener('error', (e) => console.error(e.error || e.message));
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch((e) => console.warn('SW registration failed', e));

  if (!configured) {
    $('#login-view').hidden = true;
    $('#setup-banner').hidden = false;
    return;
  }
  wireLogin();
  wireSignup();
  wireAuthChrome();
  onAuthStateChanged(auth, async (user) => {
    if (signupInProgress) return; // the sign-up form is still writing the new profile
    try {
      if (user) await startSession(user);
      else showLogin();
    } catch (e) {
      if (!['pending', 'deactivated', 'no-profile'].includes(e.code)) console.error(e);
      try { await signOut(auth); } catch { /* already signed out */ }
      if (e.code === 'pending') {
        authMessage = null;
        showLogin();
        showWaiting('Waiting for approval', e.message);
      } else {
        authMessage = { text: friendlyError(e), type: 'error' };
        showLogin();
      }
    }
  });
  window.addEventListener('online', onOnline);
  window.addEventListener('offline', updateSyncChip);
  window.addEventListener('hashchange', () => (S.uid ? renderRoute() : applyAuthHash()));
  repo.onSyncChange(updateSyncChip);
  setInterval(() => { if (S.uid && navigator.onLine && repo.pendingCount()) repo.flush(); }, 60000);
}

function showLogin() {
  S.uid = null;
  $('#app').hidden = true;
  $('#login-view').hidden = false;
  document.body.classList.remove('role-chw');
  if (!signupDone) applyAuthHash();
  document.title = 'Sign in | CHOMS';
}

const withTimeout = (p, ms) => Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(Object.assign(new Error('Request timed out'), { code: 'timeout' })), ms))]);

/**
 * After Firebase Auth accepts the password, the users/{uid} document decides everything:
 *   role pending            -> "Waiting for approval" screen (signed out)
 *   status deactivated      -> sign-in screen with a message (signed out)
 *   role admin / coordinator / chw -> that role's dashboard
 * The profile is always read FROM THE SERVER. A cached copy is only used when the device is offline,
 * and only if it was an approved, active account (see repo.cachedProfile).
 */
async function startSession(user) {
  let profile = null;
  try {
    const snap = await withTimeout(getDocFromServer(doc(db, 'users', user.uid)), 10000);
    profile = snap.exists() ? { id: snap.id, ...snap.data() } : null;
    repo.cacheProfile(user.uid, profile);
  } catch (e) {
    const offline = ['unavailable', 'timeout'].includes(e.code) || !navigator.onLine;
    if (!offline) throw e;
    profile = repo.cachedProfile(user.uid);
    if (!profile) throw Object.assign(new Error('You are offline. Sign in once with an internet connection on this device.'), { code: 'offline' });
  }
  const role = String(profile?.role || '').trim().toLowerCase();
  const status = String(profile?.status || 'active').trim().toLowerCase();
  if (role === 'pending') {
    throw Object.assign(new Error('Your account is waiting for an administrator to approve it. You can sign in as soon as that is done.'), { code: 'pending' });
  }
  if (!profile || !ACTIVE_ROLES.includes(role)) {
    throw Object.assign(new Error('Your account has no CHOMS profile. Please contact your administrator.'), { code: 'no-profile' });
  }
  if (status === 'deactivated') {
    throw Object.assign(new Error('Your account has been deactivated. Please contact your administrator.'), { code: 'deactivated' });
  }
  profile.role = role;
  profile.name = profile.name || profile.fullName || profile.email;
  authMessage = null;
  Object.assign(S, { uid: user.uid, role, profile, route: 'dashboard', search: {}, filter: {}, users: [] });
  repo.setContext({ uid: user.uid, role: profile.role, name: profile.name });
  ENT = buildEntities(S);

  $('#login-view').hidden = true;
  $('#app').hidden = false;
  document.body.classList.toggle('role-chw', S.role === 'chw');
  $('#user-name').textContent = profile.name;
  $('#user-role').textContent = ROLE_LABEL[profile.role];
  $('#user-avatar').textContent = (profile.name || '?').trim().charAt(0).toUpperCase();
  buildNav();
  updateSyncChip();
  $('#view').innerHTML = '<div class="loading">Loading your data...</div>';
  await loadAll();
  updateNavBadges();
  // fresh sign-in, or a link to a page this role cannot open -> the role's own dashboard
  const want = (location.hash.replace(/^#\/?/, '') || '').split('?')[0];
  if (freshLogin || !location.hash.startsWith('#/') || !routes().some((n) => n.id === want)) {
    freshLogin = false;
    if (location.hash !== '#/dashboard') { history.replaceState(null, '', '#/dashboard'); }
  }
  renderRoute();
}

async function loadAll() {
  if (navigator.onLine) await repo.flush().catch(() => {});
  const cols = ['beneficiaries', 'visits', 'referrals', 'appointments', ...(isStaff() ? ['reports'] : [])];
  const jobs = cols.map((c) => repo.list(c));
  if (isStaff()) jobs.push(repo.list('users'));
  const res = await Promise.allSettled(jobs);
  res.forEach((r, i) => {
    const name = i < cols.length ? cols[i] : 'users';
    if (r.status === 'fulfilled') {
      if (name === 'users') S.users = r.value;
      else S.data[name] = r.value;
    } else toast(`Could not load ${name}: ${friendlyError(r.reason)}`, 'error');
  });
  if (!isStaff()) S.users = [];
  ENT = buildEntities(S);
}

async function onOnline() {
  updateSyncChip();
  if (!S.uid) return;
  toast('Back online - syncing...', 'info', 2500);
  const r = await repo.flush();
  reportSyncResult(r);
  await refreshView();
}

function reportSyncResult(r) {
  if (r.synced) toast(`${r.synced} change${r.synced > 1 ? 's' : ''} synced to the cloud.`, 'success');
  if (r.failed.length) toast(`${r.failed.length} change(s) were rejected by the server (permissions). Contact your administrator.`, 'error', 8000);
}

async function refreshView() {
  await loadAll();
  if (!$('.modal-overlay')) renderRoute();
}

/* ------------------------------------------------------------------ */
/* Login                                                               */
/* ------------------------------------------------------------------ */
let failedLogins = 0;
let freshLogin = false;   // true right after the sign-in button: always open the dashboard
let lockedUntil = 0;

function wireLogin() {
  const form = $('#login-form');
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const err = $('#login-error');
    err.textContent = '';
    const email = form.email.value.trim();
    const password = form.password.value;
    if (Date.now() < lockedUntil) {
      err.textContent = `Too many failed attempts. Try again in ${Math.ceil((lockedUntil - Date.now()) / 1000)}s.`;
      return;
    }
    if (!isValidEmail(email)) { err.textContent = 'Enter a valid email address.'; form.email.focus(); return; }
    if (!password) { err.textContent = 'Enter your password.'; form.password.focus(); return; }
    const btn = $('button[type=submit]', form);
    btn.disabled = true;
    btn.textContent = 'Signing in...';
    authMessage = null;
    authNotice('');
    freshLogin = true;
    try {
      await signInWithEmailAndPassword(auth, email, password);
      failedLogins = 0;
      form.password.value = '';
    } catch (ex) {
      console.warn('Login failed', ex.code);
      if (++failedLogins >= 5) { lockedUntil = Date.now() + 60000; failedLogins = 0; }
      err.textContent = friendlyError(ex);
    } finally {
      btn.disabled = false;
      btn.textContent = 'Sign in';
    }
  });
  $('#forgot-link').addEventListener('click', async (e) => {
    e.preventDefault();
    const email = form.email.value.trim();
    const err = $('#login-error');
    if (!isValidEmail(email)) { err.textContent = 'Enter your email above first, then click "Forgot password".'; return; }
    try {
      await sendPasswordResetEmail(auth, email);
      err.textContent = '';
      toast('If that account exists, a password reset email has been sent.', 'success', 7000);
    } catch (ex) {
      err.textContent = friendlyError(ex);
    }
  });
}

async function logout() {
  if (repo.pendingCount() && navigator.onLine) reportSyncResult(await repo.flush());
  const n = repo.pendingCount();
  if (n) {
    const ok = await confirmDialog(`${n} change${n > 1 ? 's have' : ' has'} not synced yet. They stay saved on this device and will sync next time you sign in here. Sign out anyway?`, { confirmText: 'Sign out', danger: true });
    if (!ok) return;
  } else repo.wipeLocalData(); // POPIA: do not leave client data on a shared phone
  await signOut(auth);
  location.hash = '';
}

/* ------------------------------------------------------------------ */
/* Shell: navigation + sync chip                                       */
/* ------------------------------------------------------------------ */
function buildNav() {
  const items = navItems();
  $('#nav').innerHTML = items.map((n, i) => `<a href="#/${n.id}" data-route="${n.id}">${icon(n.id)}<span>${esc(navLabel(n))}</span>${n.id === 'users' ? '<span class="count-badge" data-pending hidden></span>' : ''}</a>`).join('');
  $('#tabbar').innerHTML = items.slice(0, 5).map((n) => `<a href="#/${n.id}" data-route="${n.id}">${icon(n.id)}<span>${esc(navLabel(n).replace('My ', ''))}</span></a>`).join('');
}

function updateSyncChip() {
  const chip = $('#sync-chip');
  if (!chip) return;
  const online = navigator.onLine;
  const n = repo.pendingCount();
  chip.className = `chip ${online ? (n ? 'chip-warn' : 'chip-ok') : 'chip-off'}`;
  chip.textContent = `${online ? 'Online' : 'Offline'} · ${n ? `${n} pending` : 'all synced'}`;
  $('#sync-btn').disabled = !online || !n;
  document.body.classList.toggle('is-offline', !online);
}

/* ------------------------------------------------------------------ */
/* Router                                                              */
/* ------------------------------------------------------------------ */
async function renderRoute() {
  let id = (location.hash.replace(/^#\/?/, '') || 'dashboard').split('?')[0];
  if (!routes().some((n) => n.id === id)) id = 'dashboard';
  S.route = id;
  $$('[data-route]').forEach((a) => a.classList.toggle('active', a.dataset.route === id));
  const nav = routes().find((n) => n.id === id);
  const title = id === 'dashboard' ? DASH_TITLE[S.role] || 'Dashboard' : navLabel(nav);
  $('#page-title').textContent = title;
  document.title = `${title} | CHOMS`;
  $('#sidebar').classList.remove('open');
  try {
    if (id === 'dashboard') renderDashboard();
    else if (ENT[id]) renderEntity(id);
    else if (id === 'reports') renderReports();
    else if (id === 'users') await renderUsers();
    else if (id === 'team') await renderTeam();
    else if (id === 'audit') await renderAudit();
  } catch (e) {
    console.error(e);
    $('#view').innerHTML = `<div class="empty"><h3>Something went wrong</h3><p>${esc(friendlyError(e))}</p></div>`;
  }
}

/* ------------------------------------------------------------------ */
/* Dashboard                                                           */
/* ------------------------------------------------------------------ */
const stat = (label, value, sub = '', go = '', tone = 'teal', ic = '') =>
  `<${go ? `a href="#/${go}"` : 'div'} class="stat tone-${tone}">${ic ? `<span class="stat-ic">${icon(ic)}</span>` : ''}<div class="stat-val">${value}</div><div class="stat-label">${esc(label)}</div>${sub ? `<div class="stat-sub">${esc(sub)}</div>` : ''}</${go ? 'a' : 'div'}>`;

function renderDashboard() {
  const { beneficiaries: B, visits: V, referrals: R, appointments: A } = S.data;
  const today = todayISO();
  const monthStart = `${today.slice(0, 8)}01`;
  const nowDT = `${today}T00:00`;
  const nextWeek = addDays(today, 7);
  const openRef = R.filter((r) => r.status === 'pending');
  const upcomingAppts = A.filter((a) => ['booked', 'rescheduled'].includes(a.status) && a.datetime >= nowDT).sort((a, b) => a.datetime.localeCompare(b.datetime));
  const scheduled = V.filter((v) => v.status === 'scheduled').sort((a, b) => a.date.localeCompare(b.date));
  const doneThisMonth = V.filter((v) => v.status === 'completed' && v.date >= monthStart).length;
  const overdue = scheduled.filter((v) => v.date < today).length;

  const stats = isStaff()
    ? [
        stat('Active beneficiaries', B.filter((b) => b.status === 'active').length, `${B.length} registered in total`, 'beneficiaries', 'coral', 'beneficiaries'),
        stat('Visits this month', doneThisMonth, `${scheduled.length} scheduled`, 'visits', 'sun', 'visits'),
        stat('Pending referrals', openRef.length, `${openRef.filter((r) => r.urgency !== 'routine').length} urgent / emergency`, 'referrals', 'sky', 'referrals'),
        stat('Upcoming appointments', upcomingAppts.length, `${A.filter((a) => a.status === 'missed').length} missed to date`, 'appointments', 'leaf', 'appointments')
      ]
    : [
        stat('My Clients', B.filter((b) => b.status === 'active').length, 'active clients assigned to you', 'beneficiaries', 'coral', 'beneficiaries'),
        stat('My Visits', scheduled.length, overdue ? `scheduled · ${overdue} overdue` : `scheduled · ${doneThisMonth} done this month`, 'visits', 'sun', 'visits'),
        stat('Referrals', openRef.length, 'pending', 'referrals', 'sky', 'referrals'),
        stat('Appointments', upcomingAppts.length, 'upcoming', 'appointments', 'leaf', 'appointments')
      ];

  const rowList = (items, fn, empty) => (items.length ? `<ul class="list">${items.map(fn).join('')}</ul>` : `<p class="muted pad">${empty}</p>`);
  const toDo = rowList(
    scheduled.filter((v) => v.date <= nextWeek).slice(0, 6),
    (v) => `<li><div><strong>${esc(ENT.helpers.benName(v.beneficiaryId))}</strong><small>${formatDate(v.date)}${v.date < today ? ' · overdue' : ''}${isStaff() ? ` · ${esc(ENT.helpers.chwName(v.chwId))}` : ''}</small></div>
      ${can(S.role, 'update', 'visits') ? `<button class="btn btn-sm btn-primary" data-action="complete-visit" data-id="${v.id}">Complete</button>` : badge(v.status)}</li>`,
    'No visits scheduled in the next 7 days.'
  );
  const appts = rowList(upcomingAppts.slice(0, 6), (a) => `<li><div><strong>${esc(ENT.helpers.benName(a.beneficiaryId))}</strong><small>${formatDateTime(a.datetime)} · ${esc(a.purpose)}</small></div>${badge(a.status)}</li>`, 'No upcoming appointments.');
  const urgent = rowList(
    openRef.sort((a, b) => (a.urgency === 'routine') - (b.urgency === 'routine')).slice(0, 6),
    (r) => `<li><div><strong>${esc(ENT.helpers.benName(r.beneficiaryId))}</strong><small>${esc(r.facility)} · ${formatDate(r.referralDate)}</small></div>${badge(r.urgency)}</li>`,
    'No pending referrals.'
  );

  let chart = '';
  if (isStaff()) {
    const counts = {};
    V.filter((v) => v.status === 'completed' && v.date >= monthStart).forEach((v) => { const n = ENT.helpers.chwName(v.chwId); counts[n] = (counts[n] || 0) + 1; });
    const max = Math.max(1, ...Object.values(counts));
    const rows = Object.entries(counts).sort((a, b) => b[1] - a[1]);
    chart = `<section class="card"><h3>Completed visits per CHW (this month)</h3>${
      rows.length ? rows.map(([n, c]) => `<div class="bar-row"><span class="bar-label">${esc(n)}</span><div class="bar"><i style="width:${(c / max) * 100}%"></i></div><b>${c}</b></div>`).join('') : '<p class="muted">No completed visits yet this month.</p>'
    }</section>`;
  }

  $('#view').innerHTML = `
    <div class="welcome"><div><h2>Hello, ${esc((S.profile.name || '').split(' ')[0])}</h2><p class="muted">${esc(ROLE_LABEL[S.role])} · ${formatDate(today)}</p></div>
      <div class="quick">
        ${can(S.role, 'create', 'visits') ? '<button class="btn btn-sun" data-action="add" data-entity="visits">+ Record visit</button>' : ''}
        ${can(S.role, 'create', 'referrals') ? '<button class="btn" data-action="add" data-entity="referrals">+ Referral</button>' : ''}
        ${can(S.role, 'create', 'beneficiaries') ? `<button class="btn" data-action="add" data-entity="beneficiaries">+ ${isStaff() ? 'Beneficiary' : 'Client'}</button>` : ''}
        ${S.role === 'coordinator' ? '<a class="btn btn-primary" href="#/reports">Generate report</a>' : ''}
      </div></div>
    ${pendingBanner()}
    ${isAdmin() ? adminPanel() : ''}
    <div class="stats">${stats.join('')}</div>
    <div class="grid-2">
      <section class="card"><h3>Visits to do (next 7 days)</h3>${toDo}</section>
      <section class="card"><h3>Upcoming appointments</h3>${appts}</section>
      <section class="card"><h3>Pending referrals</h3>${urgent}</section>
      ${chart}
    </div>`;
}

/* ------------------------------------------------------------------ */
/* Admin: overview panel, team & assignments, assign work to a CHW     */
/* ------------------------------------------------------------------ */
const ASSIGNABLE = { beneficiaries: 'assignedChwId', visits: 'chwId', appointments: 'chwId', referrals: 'chwId' };
const chws = () => S.users.filter((u) => u.role === 'chw' && u.status !== 'deactivated').sort((a, b) => String(a.name).localeCompare(String(b.name)));
const isActiveChw = (uid) => chws().some((u) => u.id === uid);
const openWork = () => ({
  beneficiaries: S.data.beneficiaries.filter((b) => b.status !== 'closed'),
  visits: S.data.visits.filter((v) => v.status === 'scheduled'),
  appointments: S.data.appointments.filter((a) => ['booked', 'rescheduled'].includes(a.status))
});
const unassigned = () => {
  const w = openWork();
  return [
    ...w.beneficiaries.filter((b) => !isActiveChw(b.assignedChwId)).map((r) => ({ key: 'beneficiaries', r })),
    ...w.visits.filter((v) => !isActiveChw(v.chwId)).map((r) => ({ key: 'visits', r })),
    ...w.appointments.filter((a) => !isActiveChw(a.chwId)).map((r) => ({ key: 'appointments', r }))
  ];
};

function adminPanel() {
  const users = S.users;
  const count = (role) => users.filter((u) => u.role === role).length;
  const loose = unassigned().length;
  return `<section class="card admin-panel"><div class="admin-panel-head"><h3>Administration</h3>
      <div class="quick"><a class="btn btn-sm" href="#/users">Users</a><a class="btn btn-sm" href="#/team">Assign work</a><a class="btn btn-sm" href="#/reports">Reports</a><a class="btn btn-sm" href="#/audit">Audit log</a></div></div>
    <div class="stats">
      ${stat('Waiting for approval', pendingUsers().length, 'new sign-ups', 'users', 'coral', 'users')}
      ${stat('Health workers', count('chw'), `${count('coordinator')} coordinator${count('coordinator') === 1 ? '' : 's'} · ${count('admin')} admin`, 'team', 'leaf', 'team')}
      ${stat('Needs a CHW', loose, loose ? 'open work with no active CHW' : 'all work is assigned', 'team', loose ? 'sun' : 'sky', 'visits')}
      ${stat('Reports to approve', S.data.reports.filter((r) => r.status !== 'approved').length, '', 'reports', 'sky', 'reports')}
    </div></section>`;
}

/** Workload per CHW + open work that has no active CHW. */
async function renderTeam() {
  try { S.users = await repo.list('users'); } catch (e) { toast(friendlyError(e), 'error'); }
  ENT = buildEntities(S);
  const w = openWork();
  const today = todayISO();
  const monthStart = `${today.slice(0, 8)}01`;
  const per = (uid) => ({
    clients: w.beneficiaries.filter((b) => b.assignedChwId === uid).length,
    visits: w.visits.filter((v) => v.chwId === uid).length,
    overdue: w.visits.filter((v) => v.chwId === uid && v.date < today).length,
    appts: w.appointments.filter((a) => a.chwId === uid).length,
    done: S.data.visits.filter((v) => v.chwId === uid && v.status === 'completed' && v.date >= monthStart).length
  });
  const H = ENT.helpers;
  const loose = unassigned();
  const label = { beneficiaries: 'Beneficiary', visits: 'Visit', appointments: 'Appointment' };
  const what = ({ key, r }) => key === 'beneficiaries' ? H.benName(r.id) : `${H.benName(r.beneficiaryId)} · ${key === 'visits' ? formatDate(r.date) : formatDateTime(r.datetime)}`;
  $('#view').innerHTML = `
    <div class="toolbar"><span class="muted">${chws().length} community health worker${chws().length === 1 ? '' : 's'}</span><span class="spacer"></span>
      <button class="btn" data-action="add" data-entity="beneficiaries">+ Beneficiary</button>
      <button class="btn" data-action="schedule-visit">Schedule visit</button>
      <button class="btn btn-primary" data-action="add" data-entity="appointments">Schedule appointment</button></div>
    <section class="card"><h3>Workload per CHW</h3>${chws().length ? `<div class="table-wrap"><table class="table"><thead><tr><th>CHW</th><th>Active clients</th><th>Scheduled visits</th><th>Overdue</th><th>Upcoming appointments</th><th>Visits done this month</th></tr></thead><tbody>
      ${chws().map((u) => { const p = per(u.id); return `<tr><td data-label="CHW"><strong>${esc(u.name)}</strong><small class="muted"> ${esc(u.phone || u.email)}</small></td><td data-label="Active clients">${p.clients}</td><td data-label="Scheduled visits">${p.visits}</td><td data-label="Overdue">${p.overdue ? badge('missed').replace(/>[^<]*</, `>${p.overdue}<`) : 0}</td><td data-label="Upcoming appointments">${p.appts}</td><td data-label="Visits done this month">${p.done}</td></tr>`; }).join('')}
      </tbody></table></div>` : '<p class="muted">No approved CHWs yet. Approve sign-ups under Users.</p>'}</section>
    <section class="card"><h3>Open work without an active CHW (${loose.length})</h3>${loose.length ? `<div class="table-wrap"><table class="table"><thead><tr><th>Type</th><th>Record</th><th>Currently</th><th></th></tr></thead><tbody>
      ${loose.map((x) => `<tr><td data-label="Type">${label[x.key]}</td><td data-label="Record">${esc(what(x))}</td><td data-label="Currently">${esc(H.chwName(x.r[ASSIGNABLE[x.key]]) || '-')}</td>
        <td class="row-actions"><button class="btn btn-sm btn-primary" data-action="assign" data-entity="${x.key}" data-id="${x.r.id}">Assign</button></td></tr>`).join('')}
      </tbody></table></div>` : '<p class="muted">Every open beneficiary, scheduled visit and appointment has an active CHW.</p>'}</section>`;
}

/** Admin: give a beneficiary / visit / appointment / referral to a CHW (optionally with the client's open work). */
function openAssign(key, id) {
  const E = ENT[key];
  const field = ASSIGNABLE[key];
  const r = S.data[E.col].find((x) => x.id === id);
  if (!r || !field) return;
  if (!chws().length) { toast('There are no approved CHWs yet. Approve someone as a CHW under Users first.', 'error'); return; }
  const H = ENT.helpers;
  const fields = [
    { name: 'chw', label: 'Community Health Worker', type: 'select', required: true, options: chws().map((u) => ({ value: u.id, label: u.name })) },
    ...(key === 'beneficiaries' ? [{ name: 'moveOpen', label: "Also move this client's scheduled visits and open appointments to this CHW", type: 'checkbox' }] : [])
  ];
  const title = key === 'beneficiaries' ? H.benName(r.id) : H.benName(r.beneficiaryId);
  const m = openModal({
    title: `Assign ${E.singular}`,
    body: `<p class="muted" style="margin-bottom:1rem">${esc(title)} · currently ${esc(H.chwName(r[field]) || 'nobody')}</p><form id="assign-form" novalidate>${formHTML(fields, { chw: isActiveChw(r[field]) ? r[field] : '', moveOpen: true })}<div class="form-error" id="form-error" role="alert"></div></form>`,
    footer: '<button type="button" class="btn" data-close>Cancel</button><button type="submit" class="btn btn-primary" form="assign-form" id="save-btn">Assign</button>'
  });
  $('#assign-form', m.el).addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = e.target.elements;
    const to = f.chw.value;
    if (!isActiveChw(to)) { showFormErrors(e.target, { chw: 'Select a CHW.' }); return; }
    const toName = H.chwName(to);
    const btn = $('#save-btn', m.el);
    btn.disabled = true;
    try {
      await repo.save(E.col, id, { [field]: to }, { isNew: false, summary: `Assigned ${E.singular} ${title} to ${toName}` });
      let moved = 0;
      if (key === 'beneficiaries' && f.moveOpen?.checked) {
        const w = openWork();
        for (const [col, list] of [['visits', w.visits], ['appointments', w.appointments]]) {
          for (const x of list.filter((x) => x.beneficiaryId === id && x.chwId !== to)) {
            await repo.save(col, x.id, { chwId: to }, { isNew: false, summary: `Reassigned ${col.slice(0, -1)} for ${title} to ${toName}` });
            moved++;
          }
        }
      }
      for (const col of ['beneficiaries', 'visits', 'appointments', 'referrals']) S.data[col] = await repo.list(col);
      ENT = buildEntities(S);
      m.close();
      toast(`Assigned to ${toName}${moved ? ` (+${moved} open visit/appointment${moved === 1 ? '' : 's'})` : ''}.`, 'success');
      updateSyncChip();
      renderRoute();
    } catch (ex) {
      $('#form-error', m.el).textContent = friendlyError(ex);
      btn.disabled = false;
    }
  });
}

/* ------------------------------------------------------------------ */
/* Generic entity list + form                                          */
/* ------------------------------------------------------------------ */
function renderEntity(key) {
  const E = ENT[key];
  const canCreate = can(S.role, 'create', E.col);
  const search = S.search[key] || '';
  const filter = S.filter[key] || '';
  $('#view').innerHTML = `
    <div class="toolbar">
      <input type="search" class="search" data-search="${key}" placeholder="Search ${esc(E.plural.toLowerCase())}..." value="${esc(search)}" aria-label="Search"/>
      <select data-filter="${key}" aria-label="Filter by status"><option value="">All statuses</option>${E.statuses.map((s) => `<option ${filter === s ? 'selected' : ''}>${s}</option>`).join('')}</select>
      <span class="spacer"></span>
      ${canCreate && key === 'visits' && isAdmin() ? '<button class="btn" data-action="schedule-visit">Schedule visit for a CHW</button>' : ''}
      ${canCreate && key === 'appointments' && isAdmin() ? '<button class="btn" data-action="add" data-entity="appointments">Schedule appointment</button>' : ''}
      ${canCreate ? `<button class="btn btn-primary" data-action="add" data-entity="${key}">+ ${esc(E.addLabel)}</button>` : ''}
    </div>
    <div class="table-wrap" id="tbl"></div>`;
  paintTable(key);
}

function paintTable(key) {
  const E = ENT[key];
  const term = S.search[key] || '';
  const status = S.filter[key] || '';
  const rows = S.data[E.col].filter((r) => (!status || r.status === status) && matchesSearch(E.searchText(r), term)).sort(E.sort);
  const tbl = $('#tbl');
  if (!rows.length) {
    tbl.innerHTML = `<div class="empty"><h3>${S.data[E.col].length ? 'No matches' : `No ${E.plural.toLowerCase()} yet`}</h3><p>${S.data[E.col].length ? 'Try a different search or filter.' : can(S.role, 'create', E.col) ? `Use the "${esc(E.addLabel)}" button to add the first one.` : 'Nothing to show.'}</p></div>`;
    return;
  }
  tbl.innerHTML = `<table class="table"><thead><tr>${E.columns.map((c) => `<th>${esc(c.label)}</th>`).join('')}<th class="actions-col"><span class="sr-only">Actions</span></th></tr></thead>
    <tbody>${rows.map((r) => `<tr>${E.columns.map((c) => `<td data-label="${esc(c.label)}">${c.render(r)}</td>`).join('')}<td class="row-actions">${rowActions(key, r)}</td></tr>`).join('')}</tbody></table>
    <div class="muted count">${rows.length} of ${S.data[E.col].length}</div>`;
}

function rowActions(key, r) {
  const b = [];
  const attr = `data-entity="${key}" data-id="${r.id}"`;
  if (key === 'beneficiaries') b.push(`<button class="btn btn-sm" data-action="view-case" data-id="${r.id}">Case file</button>`);
  if (key === 'visits' && r.status === 'scheduled' && can(S.role, 'update', 'visits')) b.push(`<button class="btn btn-sm btn-primary" data-action="complete-visit" data-id="${r.id}">Complete</button>`);
  if (key === 'appointments' && ['booked', 'rescheduled'].includes(r.status) && can(S.role, 'update', 'appointments')) b.push(`<button class="btn btn-sm" data-action="cancel-appt" data-id="${r.id}">Cancel</button>`);
  if (ASSIGNABLE[key] && can(S.role, 'assign', ENT[key].col)) b.push(`<button class="btn btn-sm" data-action="assign" ${attr}>Assign</button>`);
  if (can(S.role, 'update', ENT[key].col)) b.push(`<button class="btn btn-sm" data-action="edit" ${attr}>${key === 'appointments' ? 'Reschedule / edit' : 'Edit'}</button>`);
  if (can(S.role, 'delete', ENT[key].col)) b.push(`<button class="btn btn-sm btn-danger-ghost" data-action="delete" ${attr}>Delete</button>`);
  return b.join('');
}

function openEntityForm(key, id = null, preset = {}) {
  const E = ENT[key];
  const existing = id ? S.data[E.col].find((r) => r.id === id) : null;
  if (id && !existing) return toast('That record no longer exists.', 'error');
  if (!id && ['visits', 'referrals', 'appointments'].includes(key) && !S.data.beneficiaries.some((b) => b.status !== 'closed')) {
    return toast('Register a beneficiary first - there is nobody to link this record to.', 'error');
  }
  const fields = E.fields().filter((f) => !f.hidden);
  const values = existing ? { ...existing } : { ...E.defaults(), ...preset };
  const m = openModal({
    title: `${id ? 'Edit' : 'New'} ${E.singular}`,
    wide: true,
    body: `<form id="ent-form" novalidate>${formHTML(fields, values)}<div class="form-error" id="form-error" role="alert"></div></form>`,
    footer: '<button type="button" class="btn" data-close>Cancel</button><button type="submit" class="btn btn-primary" form="ent-form" id="save-btn">Save</button>'
  });
  const form = $('#ent-form', m.el);
  E.afterRender?.(form);
  form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    $('#form-error', form).textContent = '';
    const raw = { ...(existing || {}), ...readForm(form, fields) };
    if (isChwRole()) { raw.chwId = S.uid; raw.assignedChwId = S.uid; }
    const dateChanged = !existing || existing.datetime !== raw.datetime;
    const res = E.validate(raw, { isNew: !id, id, dateChanged });
    showFormErrors(form, res.errors);
    if (!res.valid) { $('#form-error', form).textContent = 'Please fix the highlighted fields.'; return; }
    const btn = $('#save-btn', m.el);
    btn.disabled = true;
    btn.textContent = 'Saving...';
    try {
      let data = E.prepare ? E.prepare(res.data, existing) : res.data;
      const docId = id || repo.newId(E.col);
      if (!id && E.afterCreate) data = { ...data, ...E.afterCreate(docId, data) };
      await repo.save(E.col, docId, data, { isNew: !id, summary: E.summary(data) });
      S.data[E.col] = await repo.list(E.col);
      m.close();
      toast(navigator.onLine && !repo.pendingCount() ? `${cap(E.singular)} saved.` : `${cap(E.singular)} saved on this device - it will sync when you are online.`, 'success');
      updateSyncChip();
      renderRoute();
    } catch (ex) {
      console.error(ex);
      $('#form-error', form).textContent = friendlyError(ex);
      btn.disabled = false;
      btn.textContent = 'Save';
    }
  });
}
const isChwRole = () => S.role === 'chw';
const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);

async function deleteRecord(key, id) {
  const E = ENT[key];
  const r = S.data[E.col].find((x) => x.id === id);
  if (!r) return;
  if (!(await confirmDialog(`Delete this ${E.singular}? This cannot be undone.`, { confirmText: 'Delete', danger: true }))) return;
  try {
    await repo.remove(E.col, id, { summary: E.summary(r) });
    S.data[E.col] = await repo.list(E.col);
    toast(`${cap(E.singular)} deleted.`, 'success');
    updateSyncChip();
    renderRoute();
  } catch (e) {
    toast(friendlyError(e), 'error');
  }
}

async function patchRecord(key, id, patch, okMsg) {
  const E = ENT[key];
  const r = S.data[E.col].find((x) => x.id === id);
  if (!r) return;
  try {
    await repo.save(E.col, id, { ...patch }, { isNew: false, summary: `${E.summary({ ...r, ...patch })}` });
    S.data[E.col] = await repo.list(E.col);
    toast(okMsg, 'success');
    updateSyncChip();
    renderRoute();
  } catch (e) {
    toast(friendlyError(e), 'error');
  }
}

/* ------------------------------------------------------------------ */
/* Case file (FR2 / FR9)                                               */
/* ------------------------------------------------------------------ */
function openCase(id) {
  const b = S.data.beneficiaries.find((x) => x.id === id);
  if (!b) return;
  const H = ENT.helpers;
  const hist = (arr, dateKey, line) =>
    arr.filter((x) => x.beneficiaryId === id).sort((a, c) => String(c[dateKey]).localeCompare(String(a[dateKey]))).map(line).join('') || '<li class="muted">None recorded.</li>';
  const age = calcAge(b.dob);
  const m = openModal({
    title: `Case file - ${b.firstName} ${b.lastName}`,
    wide: true,
    body: `
      <dl class="kv">
        <div><dt>Reference</dt><dd>${esc(b.ref || generateRef(b.id))}</dd></div><div><dt>ID number</dt><dd>${esc(maskId(b.idNumber))}</dd></div>
        <div><dt>Age / gender</dt><dd>${age ?? '-'} · ${esc(b.gender)}</dd></div><div><dt>Cell</dt><dd>${b.phone ? `<a href="tel:${esc(b.phone)}">${esc(b.phone)}</a>` : '-'}</dd></div>
        <div><dt>Programme</dt><dd>${esc(b.programme)}</dd></div><div><dt>Area</dt><dd>${esc(b.area)}${b.address ? `, ${esc(b.address)}` : ''}</dd></div>
        <div><dt>Assigned CHW</dt><dd>${esc(H.chwName(b.assignedChwId))}</dd></div><div><dt>Enrolled</dt><dd>${formatDate(b.enrolmentDate)} ${badge(b.status)}</dd></div>
      </dl>
      <div class="case-cols">
        <section><h4>Visits</h4><ul class="timeline">${hist(S.data.visits, 'date', (v) => `<li><b>${formatDate(v.date)}</b> ${badge(v.status)}<br><small>${esc(v.outcome || '')}${v.notes ? ` - ${esc(v.notes)}` : ''}</small></li>`)}</ul></section>
        <section><h4>Referrals</h4><ul class="timeline">${hist(S.data.referrals, 'referralDate', (r) => `<li><b>${formatDate(r.referralDate)}</b> ${badge(r.status)} ${badge(r.urgency)}<br><small>${esc(r.facility)} - ${esc(r.reason)}</small></li>`)}</ul></section>
        <section><h4>Appointments</h4><ul class="timeline">${hist(S.data.appointments, 'datetime', (a) => `<li><b>${formatDateTime(a.datetime)}</b> ${badge(a.status)}<br><small>${esc(a.purpose)}</small></li>`)}</ul></section>
      </div>`,
    footer: `<button class="btn" data-close>Close</button>
      ${can(S.role, 'create', 'visits') ? '<button class="btn" data-x="referrals">+ Referral</button><button class="btn" data-x="appointments">+ Appointment</button><button class="btn btn-primary" data-x="visits">+ Visit</button>' : ''}`
  });
  $$('[data-x]', m.el).forEach((btn) => btn.addEventListener('click', () => {
    m.close();
    openEntityForm(btn.dataset.x, null, { beneficiaryId: id, chwId: b.assignedChwId });
  }));
}

/* ------------------------------------------------------------------ */
/* Reports (FR8)                                                       */
/* ------------------------------------------------------------------ */
function renderReports() {
  const today = todayISO();
  if (!S.report.from) { S.report.from = `${today.slice(0, 8)}01`; S.report.to = today; }
  const saved = [...S.data.reports].sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)));
  $('#view').innerHTML = `
    <section class="card no-print">
      <h3>Generate programme report</h3>
      <form id="report-form" class="inline-form" novalidate>
        <label>From <input type="date" name="from" value="${S.report.from}" required/></label>
        <label>To <input type="date" name="to" value="${S.report.to}" required/></label>
        <button class="btn btn-primary" type="submit">Generate</button>
      </form><div class="err" id="report-err" role="alert"></div>
    </section>
    <div id="report-out">${S.report.summary ? reportHTML(S.report.summary) : '<div class="empty"><p>Choose a date range and click Generate.</p></div>'}</div>
    <section class="card no-print"><h3>Saved reports</h3>
      ${saved.length ? `<div class="table-wrap"><table class="table"><thead><tr><th>Period</th><th>Prepared by</th><th>Status</th><th></th></tr></thead><tbody>${saved
        .map((r) => `<tr><td data-label="Period">${formatDate(r.from)} - ${formatDate(r.to)}</td><td data-label="Prepared by">${esc(r.generatedBy || '-')}</td><td data-label="Status">${badge(r.status)}${r.approvedBy ? `<small class="muted"> by ${esc(r.approvedBy)}</small>` : ''}</td>
          <td class="row-actions"><button class="btn btn-sm" data-action="open-report" data-id="${r.id}">Open</button>
          ${can(S.role, 'approve', 'reports') && r.status !== 'approved' ? `<button class="btn btn-sm btn-primary" data-action="approve-report" data-id="${r.id}">Approve</button>` : ''}
          ${can(S.role, 'delete', 'reports') && (r.status !== 'approved' || S.role === 'admin') ? `<button class="btn btn-sm btn-danger-ghost" data-action="delete-report" data-id="${r.id}">Delete</button>` : ''}</td></tr>`)
        .join('')}</tbody></table></div>` : '<p class="muted">No saved reports yet.</p>'}
    </section>`;
  $('#report-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const f = e.target;
    const err = $('#report-err');
    err.textContent = '';
    if (!f.from.value || !f.to.value) { err.textContent = 'Both dates are required.'; return; }
    if (f.from.value > f.to.value) { err.textContent = '"From" must be on or before "To".'; return; }
    try {
      const names = Object.fromEntries(S.users.map((u) => [u.id, u.name]));
      S.report = { from: f.from.value, to: f.to.value, summary: summariseActivity(S.data, f.from.value, f.to.value, names) };
      $('#report-out').innerHTML = reportHTML(S.report.summary);
    } catch (ex) { console.error(ex); err.textContent = 'Could not generate the report.'; }
  });
}

const kvTable = (obj, title) => {
  const rows = Object.entries(obj || {});
  return `<table class="table mini"><thead><tr><th colspan="2">${esc(title)}</th></tr></thead><tbody>${rows.length ? rows.map(([k, v]) => `<tr><td>${esc(k)}</td><td class="num">${v}</td></tr>`).join('') : '<tr><td class="muted" colspan="2">No data</td></tr>'}</tbody></table>`;
};

function reportHTML(s, meta = {}) {
  return `<section class="card report"><div class="report-head"><div><h3>Programme report: ${formatDate(s.from)} to ${formatDate(s.to)}</h3>
    <p class="muted">CHOMS Community Health &amp; Outreach Management System${meta.status ? ` · ${esc(meta.status)}` : ''}</p></div>
    <div class="no-print">${meta.saved ? '' : '<button class="btn btn-primary" data-action="save-report">Save for approval</button>'}
    <button class="btn" data-action="export-report">Export CSV</button><button class="btn" data-action="print-report">Print / PDF</button></div></div>
    <div class="stats">${stat('New beneficiaries', s.newBeneficiaries)}${stat('Visits completed', s.visitsCompleted, `${s.visitsTotal} total`)}${stat('Referrals', s.referralsTotal, `${s.referralCompletionRate}% attended / completed`)}${stat('Appointments', s.appointmentsTotal, `${s.appointmentAttendanceRate}% attended`)}</div>
    <div class="grid-3">${kvTable(s.beneficiariesByProgramme, 'New beneficiaries by programme')}${kvTable(s.visitsByOutcome, 'Visit outcomes')}${kvTable(s.visitsPerChw, 'Completed visits per CHW')}${kvTable(s.referralsByStatus, 'Referrals by status')}${kvTable(s.appointmentsByStatus, 'Appointments by status')}</div></section>`;
}

function reportCSV(s) {
  const rows = [
    ['Period', `${s.from} to ${s.to}`], ['New beneficiaries', s.newBeneficiaries], ['Visits total', s.visitsTotal], ['Visits completed', s.visitsCompleted],
    ['Referrals total', s.referralsTotal], ['Referral completion rate %', s.referralCompletionRate], ['Appointments total', s.appointmentsTotal], ['Appointment attendance rate %', s.appointmentAttendanceRate]
  ];
  const add = (title, obj) => Object.entries(obj).forEach(([k, v]) => rows.push([`${title}: ${k}`, v]));
  add('Programme', s.beneficiariesByProgramme); add('Outcome', s.visitsOutcome || s.visitsByOutcome); add('CHW visits', s.visitsPerChw);
  add('Referral status', s.referralsByStatus); add('Appointment status', s.appointmentsByStatus);
  return toCSV(rows, ['Measure', 'Value']);
}

async function saveReport() {
  const s = S.report.summary;
  if (!s) return;
  try {
    await repo.save('reports', null, { from: s.from, to: s.to, summaryJson: JSON.stringify(s), status: 'pending', generatedBy: S.profile.name }, { summary: `Report ${s.from} to ${s.to}` });
    S.data.reports = await repo.list('reports');
    toast('Report saved and awaiting approval.', 'success');
    renderReports();
  } catch (e) { toast(friendlyError(e), 'error'); }
}

/* ------------------------------------------------------------------ */
/* Users (FR7 / FR11) - admin only                                     */
/* ------------------------------------------------------------------ */
const userStatus = (u) => (u.role === 'pending' ? 'pending' : u.status === 'deactivated' ? 'deactivated' : 'active');
const STATUS_LABEL = { pending: 'Pending approval', deactivated: 'Deactivated', active: 'Active' };
const statusBadge = (u) => badge({ pending: 'pending', deactivated: 'cancelled', active: 'completed' }[userStatus(u)]).replace(/>[^<]*</, `>${STATUS_LABEL[userStatus(u)]}<`);

/** All accounts: name, email, role, status + Approve / Edit / Deactivate (pending first). */
async function renderUsers({ fetch = true } = {}) {
  if (fetch) try { S.users = await repo.list('users'); } catch (e) { toast(friendlyError(e), 'error'); }
  ENT = buildEntities(S);
  const order = { pending: 0, active: 1, deactivated: 2 };
  const term = S.search.users || '';
  const roleF = S.filter.users || '';
  const rows = [...S.users]
    .filter((u) => (!roleF || u.role === roleF) && matchesSearch(`${u.name} ${u.email} ${u.role}`, term))
    .sort((a, b) => order[userStatus(a)] - order[userStatus(b)] || String(a.name).localeCompare(String(b.name)));
  updateNavBadges();
  const n = (st) => S.users.filter((u) => userStatus(u) === st).length;
  $('#view').innerHTML = `<div class="toolbar">
      <input type="search" class="search" data-search="users" placeholder="Search users..." value="${esc(term)}" aria-label="Search users"/>
      <select data-filter="users" aria-label="Filter by role"><option value="">All roles</option>${ROLES.map((r) => `<option value="${r}" ${roleF === r ? 'selected' : ''}>${esc(ROLE_LABEL[r])}</option>`).join('')}</select>
      <span class="muted">${S.users.length} users · ${n('active')} active · ${n('pending')} pending · ${n('deactivated')} deactivated</span>
      <span class="spacer"></span><button class="btn btn-primary" data-action="add-user">+ Add user</button></div>
    <div class="table-wrap"><table class="table"><thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Registered</th><th class="actions-col"><span class="sr-only">Actions</span></th></tr></thead><tbody>
    ${rows.map((u) => `<tr><td data-label="Name"><strong>${esc(u.name || u.fullName || '-')}</strong>${u.id === S.uid ? ' <small class="muted">(you)</small>' : ''}${u.phone ? `<small class="muted"> ${esc(u.phone)}</small>` : ''}</td>
      <td data-label="Email">${esc(u.email)}</td>
      <td data-label="Role">${userBadge(u)}${u.role === 'pending' && u.requestedRole ? `<small class="muted"> asked: ${esc(ROLE_LABEL[u.requestedRole] || u.requestedRole)}</small>` : ''}</td>
      <td data-label="Status">${statusBadge(u)}</td>
      <td data-label="Registered">${formatDate(String(u.createdAt || '').slice(0, 10))}</td>
      <td class="row-actions">
        ${u.role === 'pending' ? `<button class="btn btn-sm btn-primary" data-action="approve-user" data-id="${u.id}">Approve</button>` : ''}
        <button class="btn btn-sm" data-action="edit-user" data-id="${u.id}">Edit</button>
        ${u.id !== S.uid && u.role !== 'pending' ? `<button class="btn btn-sm ${userStatus(u) === 'deactivated' ? '' : 'btn-danger-ghost'}" data-action="toggle-user" data-id="${u.id}">${userStatus(u) === 'deactivated' ? 'Reactivate' : 'Deactivate'}</button>` : ''}
      </td></tr>`).join('') || '<tr><td colspan="6" class="muted">No users match.</td></tr>'}
    </tbody></table></div>`;
}

/** Deactivate = the account stays (and keeps its history) but cannot sign in or read anything. */
async function toggleUser(id) {
  const u = S.users.find((x) => x.id === id);
  if (!u || id === S.uid) return;
  const activate = userStatus(u) === 'deactivated';
  if (!(await confirmDialog(`${activate ? 'Reactivate' : 'Deactivate'} ${u.name}?${activate ? '' : ' They will no longer be able to sign in or see any records.'}`, { confirmText: activate ? 'Reactivate' : 'Deactivate', danger: !activate }))) return;
  try {
    await repo.writeOnline('users', id, { status: activate ? 'active' : 'deactivated' }, { action: 'update', summary: `${activate ? 'Reactivated' : 'Deactivated'} ${u.name}` });
    toast(`${u.name} ${activate ? 'reactivated' : 'deactivated'}.`, 'success');
    renderUsers();
  } catch (e) { toast(friendlyError(e), 'error'); }
}

/** Add (admin creates the login) or edit a user: name, phone and role. Email cannot change. */
function openUserForm(id = null) {
  const u = id ? S.users.find((x) => x.id === id) : null;
  const isSelf = !!u && u.id === S.uid;
  const roleChoices = u ? ROLES : ACTIVE_ROLES;
  const fields = [
    { name: 'name', label: 'Full name', required: true, maxlength: 80 },
    { name: 'email', label: 'Email', type: 'email', required: true, maxlength: 120, readonly: !!u },
    { name: 'phone', label: 'Cell number', type: 'tel', maxlength: 20 },
    { name: 'role', label: 'Role', type: 'select', required: true, options: roleChoices.map((r) => ({ value: r, label: ROLE_LABEL[r] })), help: isSelf ? 'You cannot change your own role.' : 'Pending = cannot sign in. CHW = own records only. Coordinator = sees all, prepares reports. Administrator = everything.' },
    ...(u ? [] : [{ name: 'password', label: 'Temporary password', type: 'password', required: true, maxlength: 64, help: 'Min 8 characters with upper-case, lower-case and a number. Ask the user to change it after first sign-in.', autocomplete: 'new-password' }])
  ];
  const m = openModal({
    title: u ? 'Edit user' : 'Add user', body: `<form id="user-form" novalidate>${formHTML(fields, u || { role: 'chw' })}<div class="form-error" id="form-error" role="alert"></div></form>`,
    footer: `${u && !isSelf ? '<button type="button" class="btn btn-danger-ghost" id="del-user-btn" style="margin-right:auto">Delete user</button>' : ''}<button type="button" class="btn" data-close>Cancel</button><button type="submit" class="btn btn-primary" form="user-form" id="save-btn">Save</button>`
  });
  const form = $('#user-form', m.el);
  if (isSelf) form.elements.role.disabled = true;
  $('#del-user-btn', m.el)?.addEventListener('click', () => { m.close(); deleteUserFlow(u.id); });
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const raw = readForm(form, fields);
    if (isSelf) raw.role = 'admin';
    const errors = {};
    const name = sanitizeText(raw.name, 80);
    const email = String(raw.email || '').trim().toLowerCase();
    const phoneRaw = String(raw.phone || '').trim();
    const phone = phoneRaw ? normalisePhone(phoneRaw) || '' : '';
    if (name.length < 3) errors.name = 'Enter the full name.';
    if (!isValidEmail(email)) errors.email = 'Enter a valid email address.';
    if (phoneRaw && !phone) errors.phone = 'Enter a valid South African number, e.g. 082 123 4567.';
    if (!roleChoices.includes(raw.role)) errors.role = 'Select a role.';
    if (!u) { const pe = validatePassword(raw.password); if (pe) errors.password = pe; }
    showFormErrors(form, errors);
    if (Object.keys(errors).length) return;
    const btn = $('#save-btn', m.el);
    btn.disabled = true;
    try {
      if (u) {
        const changes = [u.name !== name && 'name', (u.phone || '') !== phone && 'phone', u.role !== raw.role && `role ${u.role} -> ${raw.role}`].filter(Boolean);
        const approving = u.role === 'pending' && raw.role !== 'pending';
        const extra = approving ? { approvedBy: S.profile.name, approvedAt: new Date().toISOString() } : {};
        await repo.writeOnline('users', u.id, { name, phone, role: raw.role, ...extra },
          { action: approving ? 'approve' : 'update', summary: `Edited user ${name}: ${changes.join(', ') || 'no changes'}` });
      } else {
        const uid = await createAuthUser(email, raw.password);
        try {
          await repo.writeOnline('users', uid, { name, email, phone, role: raw.role, createdAt: new Date().toISOString() }, { action: 'create', summary: `Created user ${name} <${email}> (${raw.role})` });
        } catch (ex) {
          throw new Error(`The login was created but saving the profile failed (${friendlyError(ex)}). Delete the account in the Firebase console and try again.`);
        }
      }
      m.close();
      toast(u ? 'User updated.' : 'User created. Share the temporary password securely.', 'success');
      renderUsers();
    } catch (ex) {
      console.error(ex);
      $('#form-error', form).textContent = friendlyError(ex);
      btn.disabled = false;
    }
  });
}

/* ------------------------------------------------------------------ */
/* Audit trail (FR12) - admin only                                     */
/* ------------------------------------------------------------------ */
const AUDIT_TONE = { delete: 'cancelled', create: 'completed', register: 'pending', approve: 'attended', update: 'booked' };

async function renderAudit() {
  $('#view').innerHTML = '<div class="loading">Loading audit trail...</div>';
  try { S.data.auditLogs = await repo.list('auditLogs'); } catch (e) { toast(friendlyError(e), 'error'); }
  const term = S.search.audit || '';
  const act = S.filter.audit || '';
  const rows = [...S.data.auditLogs].sort((a, b) => String(b.at).localeCompare(String(a.at))).filter((r) => (!act || r.action === act) && matchesSearch(`${r.userName} ${r.action} ${r.collection} ${r.summary}`, term)).slice(0, 300);
  $('#view').innerHTML = `<div class="toolbar"><input type="search" class="search" data-search="audit" placeholder="Search audit log..." value="${esc(term)}"/><select data-filter="audit" aria-label="Filter by action"><option value="">All actions</option>${Object.keys(AUDIT_TONE).map((a) => `<option ${act === a ? 'selected' : ''}>${a}</option>`).join('')}</select><span class="spacer"></span><span class="muted">Showing latest ${rows.length}</span></div>
    <div class="table-wrap"><table class="table"><thead><tr><th>When</th><th>User</th><th>Action</th><th>Record</th><th>Detail</th></tr></thead><tbody>
    ${rows.map((r) => `<tr><td data-label="When">${esc(String(r.at).replace('T', ' ').slice(0, 16))}</td><td data-label="User">${esc(r.userName)}<small class="muted"> ${esc(r.role || '')}</small></td><td data-label="Action">${badge(AUDIT_TONE[r.action] || 'booked').replace(/>[^<]*</, `>${esc(r.action)}<`)}</td><td data-label="Record">${esc(r.collection)}</td><td data-label="Detail">${esc(r.summary)}</td></tr>`).join('') || '<tr><td colspan="5" class="muted">No audit entries.</td></tr>'}
    </tbody></table></div>`;
}

/* ------------------------------------------------------------------ */
/* Event delegation                                                    */
/* ------------------------------------------------------------------ */
const ACTIONS = {
  add: (d) => openEntityForm(d.entity),
  edit: (d) => openEntityForm(d.entity, d.id),
  delete: (d) => deleteRecord(d.entity, d.id),
  'view-case': (d) => openCase(d.id),
  'schedule-visit': () => openEntityForm('visits', null, { status: 'scheduled', date: addDays(todayISO(), 1) }),
  'complete-visit': (d) => {
    const v = S.data.visits.find((x) => x.id === d.id);
    if (v) openEntityForm('visits', d.id, { ...v, status: 'completed', date: v.date > todayISO() ? todayISO() : v.date });
  },
  'cancel-appt': async (d) => {
    if (await confirmDialog('Cancel this appointment?', { confirmText: 'Cancel appointment', danger: true })) patchRecord('appointments', d.id, { status: 'cancelled', reminderChannel: 'none', reminderDue: '' }, 'Appointment cancelled.');
  },
  'save-report': saveReport,
  'export-report': () => { const s = S.report.summary; if (s) downloadFile(`CHOMS-report-${s.from}-to-${s.to}.csv`, reportCSV(s)); },
  'print-report': () => window.print(),
  'open-report': (d) => {
    const r = S.data.reports.find((x) => x.id === d.id);
    if (!r) return;
    try {
      const s = JSON.parse(r.summaryJson);
      S.report = { from: s.from, to: s.to, summary: s };
      const m = openModal({ title: 'Saved report', wide: true, body: reportHTML(s, { saved: true, status: r.status }), footer: '<button class="btn" data-close>Close</button>' });
      $('[data-action=export-report]', m.el)?.addEventListener('click', () => ACTIONS['export-report']());
      $('[data-action=print-report]', m.el)?.addEventListener('click', () => window.print());
    } catch { toast('This report could not be read.', 'error'); }
  },
  'approve-report': async (d) => {
    if (!(await confirmDialog('Approve this report for funder submission?', { confirmText: 'Approve' }))) return;
    try {
      await repo.save('reports', d.id, { status: 'approved', approvedBy: S.profile.name, approvedAt: new Date().toISOString() }, { isNew: false, summary: 'Report approved' });
      S.data.reports = await repo.list('reports');
      toast('Report approved.', 'success');
      renderReports();
    } catch (e) { toast(friendlyError(e), 'error'); }
  },
  'delete-report': async (d) => {
    if (!(await confirmDialog('Delete this saved report?', { confirmText: 'Delete', danger: true }))) return;
    try { await repo.remove('reports', d.id, { summary: 'Report deleted' }); S.data.reports = await repo.list('reports'); renderReports(); } catch (e) { toast(friendlyError(e), 'error'); }
  },
  'add-user': () => openUserForm(),
  assign: (d) => openAssign(d.entity, d.id),
  'edit-user': (d) => openUserForm(d.id),
  'delete-user': (d) => deleteUserFlow(d.id),
  'toggle-user': (d) => toggleUser(d.id),
  'approve-user': (d) => approveUser(d.id)
};

document.addEventListener('click', (e) => {
  const t = e.target.closest('[data-action]');
  if (t && ACTIONS[t.dataset.action] && !t.closest('.modal-overlay[data-skip]')) {
    // modal-local buttons (open-report) wire their own handlers
    if (t.closest('.modal-overlay') && ['export-report', 'print-report'].includes(t.dataset.action)) return;
    ACTIONS[t.dataset.action](t.dataset);
  }
  const tg = e.target.closest('[data-toggle]');
  if (tg) { const inp = document.getElementById(tg.dataset.toggle); const show = inp.type === 'password'; inp.type = show ? 'text' : 'password'; tg.textContent = show ? 'Hide' : 'Show'; }
  if (e.target.closest('#menu-btn')) $('#sidebar').classList.toggle('open');
  else if (!e.target.closest('#sidebar')) $('#sidebar')?.classList.remove('open');
  if (e.target.closest('#logout-btn')) logout();
  if (e.target.closest('#sync-btn')) {
    repo.flush().then(async (r) => { reportSyncResult(r); await refreshView(); }).catch((ex) => toast(friendlyError(ex), 'error'));
  }
});

document.addEventListener('input', (e) => {
  const s = e.target.closest('[data-search]');
  if (!s) return;
  S.search[s.dataset.search] = s.value;
  if (s.dataset.search === 'audit') renderAudit();
  else if (s.dataset.search === 'users') renderUsers({ fetch: false }).then(() => { const el = $('[data-search=users]'); el.focus(); el.setSelectionRange(el.value.length, el.value.length); });
  else paintTable(s.dataset.search);
});

document.addEventListener('change', (e) => {
  const f = e.target.closest('[data-filter]');
  if (f) { S.filter[f.dataset.filter] = f.value; if (f.dataset.filter === 'audit') renderAudit(); else if (f.dataset.filter === 'users') renderUsers({ fetch: false }); else paintTable(f.dataset.filter); }
});

init();
