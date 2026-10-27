/** ui.js - small DOM helpers: toasts, modal dialogs, form builder/reader (Presentation layer). */
import { escapeHtml as esc } from './utils.js';

export const $ = (sel, el = document) => el.querySelector(sel);
export const $$ = (sel, el = document) => [...el.querySelectorAll(sel)];

/* ---------- toast notifications ---------- */
export function toast(message, type = 'info', ms = 4500) {
  const box = $('#toasts');
  if (!box) return;
  // avoid pile-ups: drop an identical visible toast and keep at most 3
  [...box.children].filter((t) => t.textContent === message).forEach((t) => t.remove());
  while (box.children.length >= 3) box.firstChild.remove();
  const el = document.createElement('div');
  el.className = `toast toast-${type}`;
  el.setAttribute('role', type === 'error' ? 'alert' : 'status');
  el.textContent = message;
  box.appendChild(el);
  setTimeout(() => el.remove(), ms);
}

/* ---------- modal ---------- */
export function openModal({ title, body, footer = '', wide = false }) {
  const overlay = document.createElement('div');
  overlay.className = 'modal-overlay';
  overlay.innerHTML = `
    <div class="modal ${wide ? 'modal-wide' : ''}" role="dialog" aria-modal="true" aria-label="${esc(title)}">
      <div class="modal-head"><h2>${esc(title)}</h2><button type="button" class="icon-btn" data-close aria-label="Close">&times;</button></div>
      <div class="modal-body">${body}</div>
      ${footer ? `<div class="modal-foot">${footer}</div>` : ''}
    </div>`;
  document.body.appendChild(overlay);
  document.body.classList.add('modal-open');
  const close = () => {
    overlay.remove();
    document.removeEventListener('keydown', onKey);
    if (!$('.modal-overlay')) document.body.classList.remove('modal-open');
  };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  document.addEventListener('keydown', onKey);
  overlay.addEventListener('mousedown', (e) => { if (e.target === overlay) close(); });
  $$('[data-close]', overlay).forEach((b) => b.addEventListener('click', close));
  const first = $('input:not([type=hidden]), select, textarea, button', $('.modal-body', overlay));
  if (first) first.focus();
  return { el: overlay, close };
}

export function confirmDialog(message, { confirmText = 'Confirm', danger = false } = {}) {
  return new Promise((resolve) => {
    const m = openModal({
      title: 'Please confirm',
      body: `<p>${esc(message)}</p>`,
      footer: `<button class="btn" data-no>Cancel</button><button class="btn ${danger ? 'btn-danger' : 'btn-primary'}" data-yes>${esc(confirmText)}</button>`
    });
    let done = false;
    const finish = (v) => { if (!done) { done = true; m.close(); resolve(v); } };
    $('[data-yes]', m.el).addEventListener('click', () => finish(true));
    $('[data-no]', m.el).addEventListener('click', () => finish(false));
    $('[data-close]', m.el).addEventListener('click', () => finish(false));
    m.el.addEventListener('mousedown', (e) => { if (e.target === m.el) finish(false); });
  });
}

/* ---------- form builder ---------- */
/**
 * field: { name, label, type, options, required, placeholder, help, full, rows, maxlength, readonly }
 * type: text | tel | email | password | date | datetime-local | select | textarea | checkbox
 */
export function formHTML(fields, values = {}) {
  return `<div class="form-grid">${fields.map((f) => fieldHTML(f, values[f.name])).join('')}</div>`;
}

function fieldHTML(f, value) {
  const id = `f-${f.name}`;
  const req = f.required ? ' <span class="req" aria-hidden="true">*</span>' : '';
  const common = `id="${id}" name="${f.name}" ${f.required ? 'required aria-required="true"' : ''} ${f.readonly ? 'readonly' : ''} aria-describedby="${id}-err"`;
  let control;
  if (f.type === 'select') {
    const opts = (typeof f.options === 'function' ? f.options() : f.options || []).map((o) => (typeof o === 'string' ? { value: o, label: o } : o));
    control = `<select ${common}><option value="">Select...</option>${opts
      .map((o) => `<option value="${esc(o.value)}" ${String(value ?? '') === String(o.value) ? 'selected' : ''}>${esc(o.label)}</option>`)
      .join('')}</select>`;
  } else if (f.type === 'textarea') {
    control = `<textarea ${common} rows="${f.rows || 3}" maxlength="${f.maxlength || 1000}" placeholder="${esc(f.placeholder || '')}">${esc(value ?? '')}</textarea>`;
  } else if (f.type === 'checkbox') {
    return `<div class="field full"><label class="check"><input type="checkbox" ${common} ${value ? 'checked' : ''}/> <span>${esc(f.label)}${req}</span></label>
      ${f.help ? `<small class="help">${esc(f.help)}</small>` : ''}<div class="err" id="${id}-err" role="alert"></div></div>`;
  } else {
    control = `<input type="${f.type || 'text'}" ${common} value="${esc(value ?? '')}" maxlength="${f.maxlength || 150}" placeholder="${esc(f.placeholder || '')}" ${f.type === 'tel' ? 'inputmode="tel"' : ''} ${f.autocomplete ? `autocomplete="${f.autocomplete}"` : 'autocomplete="off"'}/>`;
  }
  return `<div class="field ${f.full ? 'full' : ''}"><label for="${id}">${esc(f.label)}${req}</label>${control}
    ${f.help ? `<small class="help">${esc(f.help)}</small>` : ''}<div class="err" id="${id}-err" role="alert"></div></div>`;
}

export function readForm(formEl, fields) {
  const out = {};
  for (const f of fields) {
    const el = formEl.elements[f.name];
    if (!el) continue;
    out[f.name] = f.type === 'checkbox' ? el.checked : el.value;
  }
  return out;
}

export function showFormErrors(formEl, errors) {
  $$('.err', formEl).forEach((e) => (e.textContent = ''));
  $$('.invalid', formEl).forEach((e) => e.classList.remove('invalid'));
  let firstEl = null;
  for (const [name, msg] of Object.entries(errors)) {
    const err = $(`#f-${name}-err`, formEl);
    const input = formEl.elements[name];
    if (err) err.textContent = msg;
    if (input) {
      input.classList.add('invalid');
      input.setAttribute('aria-invalid', 'true');
      firstEl = firstEl || input;
    }
  }
  if (firstEl) firstEl.focus();
}

/* ---------- misc ---------- */
const BADGE = {
  active: 'ok', completed: 'ok', attended: 'ok', approved: 'ok', booked: 'info', rescheduled: 'info', scheduled: 'info',
  pending: 'warn', urgent: 'warn', routine: 'muted', emergency: 'danger', missed: 'danger', cancelled: 'muted', closed: 'muted', draft: 'muted',
  deactivated: 'muted', declined: 'danger'
};
export const badge = (text) => `<span class="badge badge-${BADGE[text] || 'muted'}">${esc(text)}</span>`;

/** Convert thrown Firebase / network errors into messages a CHW can understand. */
export function friendlyError(e) {
  const code = e?.code || '';
  const map = {
    'permission-denied': 'You do not have permission to do that.',
    unauthenticated: 'Your session has expired - please sign in again.',
    unavailable: 'The server is unreachable. Your changes are saved on this device and will sync later.',
    timeout: 'The request took too long. Please check your connection.',
    offline: e?.message,
    'auth/invalid-credential': 'Incorrect email or password.',
    'auth/wrong-password': 'Incorrect email or password.',
    'auth/user-not-found': 'Incorrect email or password.',
    'auth/invalid-email': 'Enter a valid email address.',
    'auth/too-many-requests': 'Too many attempts. Please wait a few minutes and try again.',
    'auth/network-request-failed': 'No internet connection. Sign-in needs a connection the first time.',
    'auth/email-already-in-use': 'An account with this email already exists.',
    'auth/weak-password': 'Password is too weak.',
    'auth/user-disabled': 'This account has been disabled.',
    // Cloud Function (deleteUser): "not-found" / bare "internal" mean the function is not deployed or unreachable
    'functions/not-found': 'The user-deletion service is not available. Deploy the deleteUser Cloud Function (see FIREBASE_SETUP.md).',
    'functions/unavailable': 'The server is unreachable. Please check your connection and try again.'
  };
  if (code === 'functions/internal' && /^internal$/i.test(e?.message || '')) return map['functions/not-found'];
  return map[code] || e?.message || 'Something went wrong. Please try again.';
}

export function downloadFile(filename, text, mime = 'text/csv;charset=utf-8') {
  const blob = new Blob(['﻿' + text], { type: mime });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}
