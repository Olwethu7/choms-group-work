/**
 * utils.js - pure, framework-free business logic for CHOMS.
 * No DOM / Firebase access here so every function can be unit tested with Jest.
 * (Business Logic layer of the Layered architecture from Part 1.)
 */

/* ------------------------------------------------------------------ */
/* Constants                                                           */
/* ------------------------------------------------------------------ */
/** pending = registered, waiting for approval | chw = field worker | coordinator = reads all, reports | admin = full access */
export const ROLES = ['pending', 'chw', 'coordinator', 'admin'];
/** Roles that may sign in and use the system. */
export const ACTIVE_ROLES = ['chw', 'coordinator', 'admin'];
/** What a person may ask to be when they sign up (an admin decides). */
export const SIGNUP_ROLES = ['chw', 'coordinator'];
export const GENDERS = ['female', 'male', 'other'];
export const PROGRAMMES = ['HIV/TB', 'Maternal Health', 'Child Health', 'Adolescent Health'];
export const BENEFICIARY_STATUSES = ['active', 'closed'];
export const VISIT_STATUSES = ['scheduled', 'completed', 'cancelled'];
export const VISIT_OUTCOMES = [
  'Stable - no action needed',
  'Follow-up visit needed',
  'Referred to facility',
  'Client not found',
  'Client refused service'
];
export const REFERRAL_URGENCY = ['routine', 'urgent', 'emergency'];
export const REFERRAL_STATUSES = ['pending', 'attended', 'completed', 'missed', 'cancelled'];
export const APPOINTMENT_STATUSES = ['booked', 'rescheduled', 'attended', 'missed', 'cancelled'];
export const REMINDER_CHANNELS = ['none', 'sms', 'email'];

const pad = (n) => String(n).padStart(2, '0');

/* ------------------------------------------------------------------ */
/* Text helpers                                                        */
/* ------------------------------------------------------------------ */

/** Escape text before inserting into HTML (prevents XSS). */
export function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])
  );
}

/** Trim, strip control characters, collapse spaces and cap the length. */
export function sanitizeText(value, max = 500, { multiline = false } = {}) {
  let s = String(value ?? '');
  // eslint-disable-next-line no-control-regex
  s = multiline ? s.replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, '') : s.replace(/[\u0000-\u001F\u007F]/g, ' ');
  s = multiline ? s.replace(/[ \t]+/g, ' ').replace(/\n{3,}/g, '\n\n') : s.replace(/\s+/g, ' ');
  return s.trim().slice(0, max);
}

/* ------------------------------------------------------------------ */
/* Date helpers                                                        */
/* ------------------------------------------------------------------ */

/** Local date as YYYY-MM-DD. */
export function todayISO(d = new Date()) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** True only for real calendar dates in YYYY-MM-DD format (rejects 2026-02-30). */
export function isValidISODate(s) {
  if (typeof s !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(s)) return false;
  const [y, m, d] = s.split('-').map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d));
  return dt.getUTCFullYear() === y && dt.getUTCMonth() === m - 1 && dt.getUTCDate() === d;
}

/** True for YYYY-MM-DDTHH:mm strings (the value of <input type="datetime-local">). */
export function isValidDateTime(s) {
  if (typeof s !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(s)) return false;
  const [date, time] = s.split('T');
  const [h, min] = time.split(':').map(Number);
  return isValidISODate(date) && h < 24 && min < 60;
}

/** Whole days from date a to date b (b - a). Negative if b is earlier. */
export function daysBetween(a, b) {
  if (!isValidISODate(a) || !isValidISODate(b)) return NaN;
  const [ya, ma, da] = a.split('-').map(Number);
  const [yb, mb, db] = b.split('-').map(Number);
  return Math.round((Date.UTC(yb, mb - 1, db) - Date.UTC(ya, ma - 1, da)) / 86400000);
}

/** Add n days (may be negative) to a YYYY-MM-DD date. */
export function addDays(iso, n) {
  if (!isValidISODate(iso)) return '';
  const [y, m, d] = iso.split('-').map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d + n));
  return `${dt.getUTCFullYear()}-${pad(dt.getUTCMonth() + 1)}-${pad(dt.getUTCDate())}`;
}

export function isFutureDate(iso, now = new Date()) {
  return isValidISODate(iso) && iso > todayISO(now);
}

export function isFutureDateTime(dt, now = new Date()) {
  return isValidDateTime(dt) && dt > `${todayISO(now)}T${pad(now.getHours())}:${pad(now.getMinutes())}`;
}

/** Age in completed years at `now`. Returns null for invalid input. */
export function calcAge(dobISO, now = new Date()) {
  if (!isValidISODate(dobISO)) return null;
  const [y, m, d] = dobISO.split('-').map(Number);
  let age = now.getFullYear() - y;
  const hadBirthday = now.getMonth() + 1 > m || (now.getMonth() + 1 === m && now.getDate() >= d);
  if (!hadBirthday) age -= 1;
  return age < 0 ? null : age;
}

/** Reminder is due 24h before an appointment (used by the SMS/email reminder queue). */
export function reminderDueAt(datetime) {
  if (!isValidDateTime(datetime)) return '';
  const [date, time] = datetime.split('T');
  return `${addDays(date, -1)}T${time}`;
}

export function formatDate(iso) {
  if (!iso) return '-';
  const s = String(iso).slice(0, 10);
  if (!isValidISODate(s)) return '-';
  const [y, m, d] = s.split('-');
  return `${d}/${m}/${y}`;
}

export function formatDateTime(dt) {
  if (!isValidDateTime(dt)) return '-';
  return `${formatDate(dt.slice(0, 10))} ${dt.slice(11, 16)}`;
}

/* ------------------------------------------------------------------ */
/* Identity / contact validation                                       */
/* ------------------------------------------------------------------ */

/** Luhn checksum used by the South African ID number. */
export function luhnValid(digits) {
  if (!/^\d+$/.test(digits)) return false;
  let sum = 0;
  let double = false;
  for (let i = digits.length - 1; i >= 0; i--) {
    let n = Number(digits[i]);
    if (double) {
      n *= 2;
      if (n > 9) n -= 9;
    }
    sum += n;
    double = !double;
  }
  return sum % 10 === 0;
}

/**
 * Validate a 13-digit South African ID number and extract date of birth / gender.
 * Format: YYMMDD SSSS C A Z  (SSSS >= 5000 = male, C = citizen flag, Z = Luhn digit)
 */
export function parseSAId(id, now = new Date()) {
  const s = String(id ?? '').replace(/\s/g, '');
  if (!/^\d{13}$/.test(s)) return { valid: false, error: 'ID number must be exactly 13 digits.' };
  const yy = Number(s.slice(0, 2));
  const mm = s.slice(2, 4);
  const dd = s.slice(4, 6);
  let year = yy > now.getFullYear() % 100 ? 1900 + yy : 2000 + yy;
  let dob = `${year}-${mm}-${dd}`;
  if (!isValidISODate(dob)) return { valid: false, error: 'ID number contains an invalid date of birth.' };
  if (dob > todayISO(now)) {
    year -= 100;
    dob = `${year}-${mm}-${dd}`;
  }
  if (!luhnValid(s)) return { valid: false, error: 'ID number failed the checksum - please re-check the digits.' };
  return {
    valid: true,
    dob,
    gender: Number(s.slice(6, 10)) >= 5000 ? 'male' : 'female',
    citizen: s[10] === '0'
  };
}

/** Accepts 0821234567, 082 123 4567, +27821234567, 27821234567 -> normalised to +27XXXXXXXXX. */
export function normalisePhone(raw) {
  const s = String(raw ?? '').replace(/[\s\-()]/g, '');
  let m = s.match(/^0([1-8]\d{8})$/) || s.match(/^\+?27([1-8]\d{8})$/);
  return m ? `+27${m[1]}` : null;
}

export function isValidEmail(email) {
  const s = String(email ?? '').trim();
  return s.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(s);
}

/** At least 8 chars with upper, lower and a digit. */
export function validatePassword(pw) {
  const s = String(pw ?? '');
  if (s.length < 8) return 'Password must be at least 8 characters.';
  if (!/[a-z]/.test(s) || !/[A-Z]/.test(s) || !/\d/.test(s))
    return 'Password must contain an upper-case letter, a lower-case letter and a number.';
  return '';
}

/** Short, human-friendly reference. Uses the first 5 chars of the document id so it is unique even when created offline. */
export function generateRef(id, year = new Date().getFullYear()) {
  const part = String(id ?? '').replace(/[^a-z0-9]/gi, '').slice(0, 5).toUpperCase().padEnd(5, '0');
  return `MCH-${year}-${part}`;
}

export function maskId(id) {
  const s = String(id ?? '');
  return s.length >= 4 ? `${'*'.repeat(s.length - 4)}${s.slice(-4)}` : s ? '****' : '-';
}

const NAME_RE = /^[\p{L}][\p{L}\s'.-]*$/u;

/* ------------------------------------------------------------------ */
/* Entity validators - each returns { valid, errors, data }            */
/* ------------------------------------------------------------------ */
const result = (errors, data) => ({ valid: Object.keys(errors).length === 0, errors, data });
const inList = (v, list) => list.includes(v);

export function validateBeneficiary(input, ctx = {}) {
  const e = {};
  const d = {};
  const now = ctx.now || new Date();

  d.firstName = sanitizeText(input.firstName, 60);
  d.lastName = sanitizeText(input.lastName, 60);
  if (d.firstName.length < 2 || !NAME_RE.test(d.firstName)) e.firstName = 'Enter a valid first name (letters only, min 2 characters).';
  if (d.lastName.length < 2 || !NAME_RE.test(d.lastName)) e.lastName = 'Enter a valid surname (letters only, min 2 characters).';

  d.idNumber = String(input.idNumber ?? '').replace(/\s/g, '');
  d.dob = sanitizeText(input.dob, 10);
  d.gender = input.gender || '';

  if (d.idNumber) {
    const parsed = parseSAId(d.idNumber, now);
    if (!parsed.valid) e.idNumber = parsed.error;
    else {
      if (d.dob && d.dob !== parsed.dob) e.dob = 'Date of birth does not match the ID number.';
      if (!d.dob) d.dob = parsed.dob;
      if (!d.gender) d.gender = parsed.gender;
      const dup = (ctx.existing || []).find((b) => b.idNumber === d.idNumber && b.id !== ctx.id);
      if (dup) e.idNumber = `This ID number is already registered (${dup.firstName} ${dup.lastName}).`;
    }
  }
  if (!d.dob) e.dob = e.dob || 'Date of birth is required when no ID number is supplied.';
  else if (!isValidISODate(d.dob)) e.dob = 'Enter a valid date of birth.';
  else if (isFutureDate(d.dob, now)) e.dob = 'Date of birth cannot be in the future.';
  else if (calcAge(d.dob, now) > 120) e.dob = 'Date of birth is not plausible.';

  if (!inList(d.gender, GENDERS)) e.gender = 'Select a gender.';

  d.phone = '';
  if (String(input.phone ?? '').trim()) {
    const p = normalisePhone(input.phone);
    if (!p) e.phone = 'Enter a valid South African number, e.g. 082 123 4567.';
    else d.phone = p;
  }
  d.address = sanitizeText(input.address, 200);
  d.area = sanitizeText(input.area, 80);
  if (d.area.length < 2) e.area = 'Area / ward is required.';

  d.programme = input.programme || '';
  if (!inList(d.programme, PROGRAMMES)) e.programme = 'Select a programme.';

  d.enrolmentDate = sanitizeText(input.enrolmentDate, 10);
  if (!isValidISODate(d.enrolmentDate)) e.enrolmentDate = 'Enter a valid enrolment date.';
  else if (isFutureDate(d.enrolmentDate, now)) e.enrolmentDate = 'Enrolment date cannot be in the future.';

  d.status = input.status || 'active';
  if (!inList(d.status, BENEFICIARY_STATUSES)) e.status = 'Invalid status.';

  d.assignedChwId = input.assignedChwId || '';
  if (!d.assignedChwId) e.assignedChwId = 'Assign a Community Health Worker.';

  d.consent = input.consent === true || input.consent === 'true' || input.consent === 'on';
  if (!d.consent) e.consent = 'POPIA consent must be recorded before registering a beneficiary.';

  return result(e, d);
}

export function validateVisit(input, ctx = {}) {
  const e = {};
  const d = {};
  const now = ctx.now || new Date();
  d.beneficiaryId = input.beneficiaryId || '';
  if (!d.beneficiaryId) e.beneficiaryId = 'Select a beneficiary.';
  d.chwId = input.chwId || '';
  if (!d.chwId) e.chwId = 'Select the Community Health Worker.';
  d.status = input.status || 'completed';
  if (!inList(d.status, VISIT_STATUSES)) e.status = 'Invalid status.';
  d.date = sanitizeText(input.date, 10);
  if (!isValidISODate(d.date)) e.date = 'Enter a valid visit date.';
  d.location = sanitizeText(input.location, 150);
  d.notes = sanitizeText(input.notes, 2000, { multiline: true });
  d.outcome = input.outcome || '';

  if (d.status === 'completed') {
    if (!e.date && isFutureDate(d.date, now)) e.date = 'A completed visit cannot be dated in the future.';
    if (d.location.length < 2) e.location = 'Location is required for a completed visit.';
    if (!inList(d.outcome, VISIT_OUTCOMES)) e.outcome = 'Select the visit outcome.';
    if (d.notes.length < 10) e.notes = 'Visit notes are required (min 10 characters).';
  } else if (d.status === 'scheduled' && ctx.isNew && !e.date && d.date < todayISO(now)) {
    e.date = 'A new scheduled visit cannot be in the past.';
  }
  return result(e, d);
}

export function validateReferral(input, ctx = {}) {
  const e = {};
  const d = {};
  const now = ctx.now || new Date();
  d.beneficiaryId = input.beneficiaryId || '';
  if (!d.beneficiaryId) e.beneficiaryId = 'Select a beneficiary.';
  d.chwId = input.chwId || '';
  if (!d.chwId) e.chwId = 'Select the Community Health Worker.';
  d.facility = sanitizeText(input.facility, 120);
  if (d.facility.length < 3) e.facility = 'Enter the receiving health facility.';
  d.reason = sanitizeText(input.reason, 1000, { multiline: true });
  if (d.reason.length < 5) e.reason = 'Enter the reason for referral (min 5 characters).';
  d.urgency = input.urgency || 'routine';
  if (!inList(d.urgency, REFERRAL_URGENCY)) e.urgency = 'Select urgency.';
  d.referralDate = sanitizeText(input.referralDate, 10);
  if (!isValidISODate(d.referralDate)) e.referralDate = 'Enter a valid referral date.';
  else if (isFutureDate(d.referralDate, now)) e.referralDate = 'Referral date cannot be in the future.';
  d.status = input.status || 'pending';
  if (!inList(d.status, REFERRAL_STATUSES)) e.status = 'Invalid status.';
  d.outcomeNotes = sanitizeText(input.outcomeNotes, 1000, { multiline: true });
  if (['attended', 'completed'].includes(d.status) && d.outcomeNotes.length < 5)
    e.outcomeNotes = 'Record the outcome before marking the referral attended/completed.';
  return result(e, d);
}

export function validateAppointment(input, ctx = {}) {
  const e = {};
  const d = {};
  const now = ctx.now || new Date();
  d.beneficiaryId = input.beneficiaryId || '';
  if (!d.beneficiaryId) e.beneficiaryId = 'Select a beneficiary.';
  d.chwId = input.chwId || '';
  if (!d.chwId) e.chwId = 'Select the Community Health Worker.';
  d.datetime = sanitizeText(input.datetime, 16);
  if (!isValidDateTime(d.datetime)) e.datetime = 'Enter a valid date and time.';
  d.purpose = sanitizeText(input.purpose, 150);
  if (d.purpose.length < 3) e.purpose = 'Enter the purpose / facility (min 3 characters).';
  d.status = input.status || 'booked';
  if (!inList(d.status, APPOINTMENT_STATUSES)) e.status = 'Invalid status.';
  d.reminderChannel = input.reminderChannel || 'none';
  if (!inList(d.reminderChannel, REMINDER_CHANNELS)) e.reminderChannel = 'Invalid reminder option.';
  d.notes = sanitizeText(input.notes, 1000, { multiline: true });
  if (!e.datetime && ['booked', 'rescheduled'].includes(d.status) && ctx.dateChanged !== false && !isFutureDateTime(d.datetime, now))
    e.datetime = 'Bookings and reschedules must be in the future.';
  d.reminderDue = d.reminderChannel !== 'none' && !e.datetime ? reminderDueAt(d.datetime) : '';
  return result(e, d);
}

/* ------------------------------------------------------------------ */
/* Role-based access control (mirrors firestore.rules)                 */
/* ------------------------------------------------------------------ */
const CORE = ['beneficiaries', 'visits', 'referrals', 'appointments'];

/**
 * can('chw', 'delete', 'beneficiaries') -> false
 * Actions: read | create | update | delete | approve | assign
 * admin: everything (incl. assigning work to a CHW).
 * coordinator: reads all programme data, prepares reports; cannot change records, approve reports or manage users.
 * chw: their own beneficiaries / visits / referrals / appointments (no beneficiary delete).
 * pending: nothing. The UI uses this to hide buttons; Firestore rules enforce the same matrix server-side.
 */
export function can(role, action, collection) {
  if (role === 'admin') return true;
  if (role === 'coordinator') {
    if (collection === 'reports') return ['read', 'create', 'update', 'delete'].includes(action);
    return CORE.includes(collection) && action === 'read';
  }
  if (role !== 'chw') return false;
  if (action === 'assign') return false;
  if (!CORE.includes(collection)) return false; // reports, users, auditLogs: not for CHWs
  if (action === 'delete') return collection !== 'beneficiaries';
  return ['read', 'create', 'update'].includes(action);
}

/* ------------------------------------------------------------------ */
/* Reporting                                                           */
/* ------------------------------------------------------------------ */
export function inRange(dateStr, from, to) {
  const d = String(dateStr ?? '').slice(0, 10);
  if (!isValidISODate(d)) return false;
  if (from && d < from) return false;
  if (to && d > to) return false;
  return true;
}

const countBy = (items, keyFn) =>
  items.reduce((acc, it) => {
    const k = keyFn(it) || 'unknown';
    acc[k] = (acc[k] || 0) + 1;
    return acc;
  }, {});

/** Summarise programme activity between two dates (inclusive) for funder reports. */
export function summariseActivity({ beneficiaries = [], visits = [], referrals = [], appointments = [] }, from, to, chwNames = {}) {
  const b = beneficiaries.filter((x) => inRange(x.enrolmentDate, from, to));
  const v = visits.filter((x) => inRange(x.date, from, to));
  const r = referrals.filter((x) => inRange(x.referralDate, from, to));
  const a = appointments.filter((x) => inRange(x.datetime, from, to));
  const completed = v.filter((x) => x.status === 'completed');
  const visitsPerChw = countBy(completed, (x) => chwNames[x.chwId] || x.chwId);
  const closedReferrals = r.filter((x) => ['attended', 'completed'].includes(x.status)).length;
  return {
    from,
    to,
    newBeneficiaries: b.length,
    beneficiariesByProgramme: countBy(b, (x) => x.programme),
    visitsTotal: v.length,
    visitsCompleted: completed.length,
    visitsByOutcome: countBy(completed, (x) => x.outcome),
    visitsPerChw,
    referralsTotal: r.length,
    referralsByStatus: countBy(r, (x) => x.status),
    referralCompletionRate: r.length ? Math.round((closedReferrals / r.length) * 100) : 0,
    appointmentsTotal: a.length,
    appointmentsByStatus: countBy(a, (x) => x.status),
    appointmentAttendanceRate: a.length ? Math.round((a.filter((x) => x.status === 'attended').length / a.length) * 100) : 0
  };
}

/** CSV with formula-injection protection (cells starting = + - @ are prefixed with '). */
export function toCSV(rows, headers) {
  const cell = (v) => {
    let s = String(v ?? '');
    if (/^[=+\-@]/.test(s)) s = `'${s}`;
    return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  return [headers.map(cell).join(','), ...rows.map((r) => r.map(cell).join(','))].join('\r\n');
}

/** Case-insensitive multi-term search over several fields. */
export function matchesSearch(text, term) {
  const t = String(term ?? '').trim().toLowerCase();
  if (!t) return true;
  const hay = String(text ?? '').toLowerCase();
  return t.split(/\s+/).every((w) => hay.includes(w));
}
