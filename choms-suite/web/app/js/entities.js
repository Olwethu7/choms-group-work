/**
 * entities.js - declarative definition of the four CRUD entities (fields, table columns, validation).
 * One generic form/table engine in app.js renders all of them, which keeps the code DRY and consistent.
 */
import {
  GENDERS, PROGRAMMES, BENEFICIARY_STATUSES, VISIT_STATUSES, VISIT_OUTCOMES, REFERRAL_URGENCY, REFERRAL_STATUSES,
  APPOINTMENT_STATUSES, REMINDER_CHANNELS, todayISO, escapeHtml as esc, formatDate, formatDateTime, maskId, generateRef,
  validateBeneficiary, validateVisit, validateReferral, validateAppointment, parseSAId
} from './utils.js';
import { badge } from './ui.js';

export function buildEntities(S) {
  // a CHW only sees and records their own clients
  const isChw = S.role === 'chw';
  const ben = (id) => S.data.beneficiaries.find((b) => b.id === id);
  const benName = (id) => (ben(id) ? `${ben(id).firstName} ${ben(id).lastName}` : 'Unknown');
  const chwName = (id) => S.users.find((u) => u.id === id)?.name || (id === S.uid ? S.profile.name : '-');
  const benOptions = () =>
    S.data.beneficiaries
      .filter((b) => b.status !== 'closed')
      .sort((a, b) => a.lastName.localeCompare(b.lastName))
      .map((b) => ({ value: b.id, label: `${b.firstName} ${b.lastName} (${b.ref || generateRef(b.id)})` }));
  const chwOptions = () => S.users.filter((u) => u.role === 'chw' && u.status !== 'deactivated').map((u) => ({ value: u.id, label: u.name }));
  const chwField = (name, label) => ({ name, label, type: 'select', options: chwOptions, required: true, hidden: isChw });

  /** When a beneficiary is picked, default the CHW select to that beneficiary's CHW. */
  const linkChw = (form) => {
    const b = form.elements.beneficiaryId;
    const c = form.elements.chwId;
    if (b && c) b.addEventListener('change', () => { if (!c.value && ben(b.value)) c.value = ben(b.value).assignedChwId || ''; });
  };

  return {
    beneficiaries: {
      key: 'beneficiaries', col: 'beneficiaries', singular: 'beneficiary', plural: 'Beneficiaries',
      title: isChw ? 'My Clients' : 'Beneficiaries', addLabel: isChw ? 'Register client' : 'Register beneficiary',
      statuses: BENEFICIARY_STATUSES,
      ownerField: 'assignedChwId',
      fields: () => [
        { name: 'firstName', label: 'First name', required: true, maxlength: 60 },
        { name: 'lastName', label: 'Surname', required: true, maxlength: 60 },
        { name: 'idNumber', label: 'SA ID number', maxlength: 13, help: 'Optional. 13 digits - date of birth and gender fill in automatically.', type: 'tel' },
        { name: 'dob', label: 'Date of birth', type: 'date', required: true },
        { name: 'gender', label: 'Gender', type: 'select', options: GENDERS.map((g) => ({ value: g, label: g[0].toUpperCase() + g.slice(1) })), required: true },
        { name: 'phone', label: 'Cell number', type: 'tel', placeholder: '082 123 4567' },
        { name: 'area', label: 'Area / ward', required: true, maxlength: 80 },
        { name: 'programme', label: 'Programme', type: 'select', options: PROGRAMMES, required: true },
        { name: 'enrolmentDate', label: 'Enrolment date', type: 'date', required: true },
        chwField('assignedChwId', 'Assigned CHW'),
        { name: 'status', label: 'Status', type: 'select', options: BENEFICIARY_STATUSES },
        { name: 'address', label: 'Street address', full: true, maxlength: 200 },
        { name: 'consent', label: 'The beneficiary has given informed consent for their information to be stored and used by MatCH (POPIA).', type: 'checkbox', required: true, help: 'Required - registration cannot be saved without consent.' }
      ],
      defaults: () => ({ enrolmentDate: todayISO(), status: 'active', assignedChwId: isChw ? S.uid : '' }),
      validate: (raw, ctx) => validateBeneficiary(raw, { ...ctx, existing: S.data.beneficiaries }),
      afterCreate: (id) => ({ ref: generateRef(id) }),
      summary: (d) => `${d.firstName} ${d.lastName}`,
      afterRender(form) {
        const idEl = form.elements.idNumber;
        idEl.addEventListener('input', () => {
          const p = parseSAId(idEl.value);
          if (p.valid) {
            if (!form.elements.dob.value) form.elements.dob.value = p.dob;
            if (!form.elements.gender.value) form.elements.gender.value = p.gender;
          }
        });
      },
      columns: [
        { label: 'Ref', render: (r) => esc(r.ref || generateRef(r.id)) },
        { label: 'Name', render: (r) => `<strong>${esc(r.firstName)} ${esc(r.lastName)}</strong>` },
        { label: 'ID', render: (r) => esc(maskId(r.idNumber)) },
        { label: 'Programme', render: (r) => esc(r.programme) },
        { label: 'Area', render: (r) => esc(r.area) },
        ...(isChw ? [] : [{ label: 'CHW', render: (r) => esc(chwName(r.assignedChwId)) }]),
        { label: 'Status', render: (r) => badge(r.status) }
      ],
      sort: (a, b) => `${a.lastName}${a.firstName}`.localeCompare(`${b.lastName}${b.firstName}`),
      searchText: (r) => `${r.firstName} ${r.lastName} ${r.ref || ''} ${r.idNumber || ''} ${r.area} ${r.programme} ${r.phone || ''}`
    },

    visits: {
      key: 'visits', col: 'visits', singular: 'visit', plural: 'Visits', title: isChw ? 'My Visits' : 'Visits & Scheduling',
      addLabel: 'Record visit', statuses: VISIT_STATUSES,
      fields: () => [
        { name: 'beneficiaryId', label: 'Beneficiary', type: 'select', options: benOptions, required: true, full: true },
        chwField('chwId', 'Community Health Worker'),
        { name: 'status', label: 'Status', type: 'select', options: VISIT_STATUSES, required: true },
        { name: 'date', label: 'Visit date', type: 'date', required: true },
        { name: 'location', label: 'Location', maxlength: 150, placeholder: 'e.g. Home visit - Umlazi Section K' },
        { name: 'outcome', label: 'Outcome', type: 'select', options: VISIT_OUTCOMES, full: true, help: 'Required once the visit is completed.' },
        { name: 'notes', label: 'Visit notes', type: 'textarea', full: true, rows: 4, maxlength: 2000 }
      ],
      defaults: () => ({ status: 'completed', date: todayISO(), chwId: isChw ? S.uid : '' }),
      validate: (raw, ctx) => validateVisit(raw, ctx),
      summary: (d) => `${benName(d.beneficiaryId)} - ${d.status}`,
      afterRender: linkChw,
      columns: [
        { label: 'Date', render: (r) => formatDate(r.date) },
        { label: 'Beneficiary', render: (r) => `<strong>${esc(benName(r.beneficiaryId))}</strong>` },
        ...(isChw ? [] : [{ label: 'CHW', render: (r) => esc(chwName(r.chwId)) }]),
        { label: 'Status', render: (r) => badge(r.status) },
        { label: 'Outcome', render: (r) => esc(r.outcome || '-') },
        { label: 'Location', render: (r) => esc(r.location || '-') }
      ],
      sort: (a, b) => String(b.date).localeCompare(String(a.date)),
      searchText: (r) => `${benName(r.beneficiaryId)} ${chwName(r.chwId)} ${r.location || ''} ${r.outcome || ''} ${r.notes || ''}`
    },

    referrals: {
      key: 'referrals', col: 'referrals', singular: 'referral', plural: 'Referrals', title: 'Referrals',
      addLabel: 'New referral', statuses: REFERRAL_STATUSES,
      fields: () => [
        { name: 'beneficiaryId', label: 'Beneficiary', type: 'select', options: benOptions, required: true, full: true },
        chwField('chwId', 'Referred by (CHW)'),
        { name: 'facility', label: 'Receiving facility', required: true, maxlength: 120, placeholder: 'e.g. Prince Mshiyeni Hospital' },
        { name: 'urgency', label: 'Urgency', type: 'select', options: REFERRAL_URGENCY, required: true },
        { name: 'referralDate', label: 'Referral date', type: 'date', required: true },
        { name: 'status', label: 'Status', type: 'select', options: REFERRAL_STATUSES, required: true },
        { name: 'reason', label: 'Reason for referral', type: 'textarea', required: true, full: true, maxlength: 1000 },
        { name: 'outcomeNotes', label: 'Outcome / feedback from facility', type: 'textarea', full: true, maxlength: 1000, help: 'Required when marked attended or completed.' }
      ],
      defaults: () => ({ urgency: 'routine', status: 'pending', referralDate: todayISO(), chwId: isChw ? S.uid : '' }),
      validate: (raw, ctx) => validateReferral(raw, ctx),
      summary: (d) => `${benName(d.beneficiaryId)} to ${d.facility} (${d.status})`,
      afterRender: linkChw,
      columns: [
        { label: 'Date', render: (r) => formatDate(r.referralDate) },
        { label: 'Beneficiary', render: (r) => `<strong>${esc(benName(r.beneficiaryId))}</strong>` },
        { label: 'Facility', render: (r) => esc(r.facility) },
        { label: 'Urgency', render: (r) => badge(r.urgency) },
        { label: 'Status', render: (r) => badge(r.status) }
      ],
      sort: (a, b) => String(b.referralDate).localeCompare(String(a.referralDate)),
      searchText: (r) => `${benName(r.beneficiaryId)} ${r.facility} ${r.reason} ${r.urgency}`
    },

    appointments: {
      key: 'appointments', col: 'appointments', singular: 'appointment', plural: 'Appointments', title: 'Appointments',
      addLabel: 'Book appointment', statuses: APPOINTMENT_STATUSES,
      fields: () => [
        { name: 'beneficiaryId', label: 'Beneficiary', type: 'select', options: benOptions, required: true, full: true },
        chwField('chwId', 'Community Health Worker'),
        { name: 'datetime', label: 'Date & time', type: 'datetime-local', required: true },
        { name: 'purpose', label: 'Purpose / facility', required: true, maxlength: 150, placeholder: 'e.g. ANC check-up - Umlazi Clinic' },
        { name: 'status', label: 'Status', type: 'select', options: APPOINTMENT_STATUSES, required: true },
        { name: 'reminderChannel', label: 'Reminder', type: 'select', options: REMINDER_CHANNELS.map((c) => ({ value: c, label: c === 'none' ? 'No reminder' : c.toUpperCase() })), help: 'Queued 24h before the appointment.' },
        { name: 'notes', label: 'Notes', type: 'textarea', full: true, maxlength: 1000 }
      ],
      defaults: () => ({ status: 'booked', reminderChannel: 'sms', chwId: isChw ? S.uid : '' }),
      validate: (raw, ctx) => validateAppointment(raw, ctx),
      /** Changing the date/time of a booked appointment automatically marks it as rescheduled. */
      prepare: (data, existing) =>
        existing && existing.datetime !== data.datetime && data.status === 'booked' ? { ...data, status: 'rescheduled' } : data,
      summary: (d) => `${benName(d.beneficiaryId)} @ ${formatDateTime(d.datetime)} (${d.status})`,
      afterRender: linkChw,
      columns: [
        { label: 'When', render: (r) => formatDateTime(r.datetime) },
        { label: 'Beneficiary', render: (r) => `<strong>${esc(benName(r.beneficiaryId))}</strong>` },
        { label: 'Purpose', render: (r) => esc(r.purpose) },
        { label: 'Status', render: (r) => badge(r.status) },
        { label: 'Reminder', render: (r) => esc(r.reminderChannel && r.reminderChannel !== 'none' ? r.reminderChannel.toUpperCase() : '-') }
      ],
      sort: (a, b) => String(b.datetime).localeCompare(String(a.datetime)),
      searchText: (r) => `${benName(r.beneficiaryId)} ${r.purpose} ${r.notes || ''}`
    },
    helpers: { benName, chwName, ben }
  };
}
