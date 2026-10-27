package com.match.choms.util;

import com.match.choms.model.Appointment;
import com.match.choms.model.Beneficiary;
import com.match.choms.model.Referral;
import com.match.choms.model.Visit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Form validation. These are the same business rules as js/utils.js in the web app.
 * Every validate method returns a map of fieldName -> error message (empty map = valid).
 * Pure Java (no Firebase), so the logic is easy to unit test.
 */
public final class Validators {
    private Validators() { }

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final Pattern NAME = Pattern.compile("^\\p{L}[\\p{L}\\s'.-]*$");

    // ---------------------------------------------------------------- generic helpers

    /** Trim, strip control characters, collapse spaces, and cap the length. */
    public static String clean(String value, int max) {
        if (value == null) return "";
        String s = value.replaceAll("[\\u0000-\\u001F\\u007F]", " ").replaceAll("\\s+", " ").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** Like clean() but keeps line breaks (for notes). */
    public static String cleanMultiline(String value, int max) {
        if (value == null) return "";
        String s = value.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]", "")
                .replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    public static boolean isValidEmail(String email) {
        return email != null && email.length() <= 254 && EMAIL.matcher(email.trim()).matches();
    }

    /** Luhn checksum used by the South African ID number. */
    public static boolean luhnValid(String digits) {
        if (digits == null || !digits.matches("\\d+")) return false;
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (dbl) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }

    /** Result of reading a South African ID number. */
    public static class SaId {
        public boolean valid;
        public String error;
        public String dob;
        public String gender;
    }

    /** Validate a 13-digit SA ID and extract date of birth and gender. */
    public static SaId parseSaId(String raw) {
        SaId r = new SaId();
        String s = raw == null ? "" : raw.replaceAll("\\s", "");
        if (!s.matches("\\d{13}")) {
            r.error = "ID number must be exactly 13 digits.";
            return r;
        }
        int yy = Integer.parseInt(s.substring(0, 2));
        int currentYY = Integer.parseInt(DateUtils.todayISO().substring(2, 4));
        int year = yy > currentYY ? 1900 + yy : 2000 + yy;
        String dob = year + "-" + s.substring(2, 4) + "-" + s.substring(4, 6);
        if (!DateUtils.isValidISODate(dob)) {
            r.error = "ID number contains an invalid date of birth.";
            return r;
        }
        if (DateUtils.isFuture(dob)) {
            year -= 100;
            dob = year + "-" + s.substring(2, 4) + "-" + s.substring(4, 6);
        }
        if (!luhnValid(s)) {
            r.error = "ID number failed the checksum - please re-check the digits.";
            return r;
        }
        r.valid = true;
        r.dob = dob;
        r.gender = Integer.parseInt(s.substring(6, 10)) >= 5000 ? "male" : "female";
        return r;
    }

    /** 0821234567 / 082 123 4567 / +27821234567 -> +27821234567, or null when invalid. */
    public static String normalisePhone(String raw) {
        String s = raw == null ? "" : raw.replaceAll("[\\s\\-()]", "");
        java.util.regex.Matcher m = Pattern.compile("^0([1-8]\\d{8})$").matcher(s);
        if (m.matches()) return "+27" + m.group(1);
        m = Pattern.compile("^\\+?27([1-8]\\d{8})$").matcher(s);
        if (m.matches()) return "+27" + m.group(1);
        return null;
    }

    // ---------------------------------------------------------------- login

    /** Validate the login form. Returns field -> error. */
    public static Map<String, String> validateLogin(String email, String password) {
        Map<String, String> e = new LinkedHashMap<>();
        if (email == null || email.trim().isEmpty()) e.put("email", "Email is required.");
        else if (!isValidEmail(email)) e.put("email", "Enter a valid email address.");
        if (password == null || password.isEmpty()) e.put("password", "Password is required.");
        return e;
    }

    // ---------------------------------------------------------------- beneficiary

    /**
     * Validates AND cleans the beneficiary (the object is updated in place with the cleaned values,
     * e.g. normalised phone number, DOB/gender filled in from the ID number).
     * @param existing already loaded beneficiaries - used to detect a duplicate ID number
     */
    public static Map<String, String> validateBeneficiary(Beneficiary b, List<Beneficiary> existing) {
        Map<String, String> e = new LinkedHashMap<>();

        b.firstName = clean(b.firstName, 60);
        b.lastName = clean(b.lastName, 60);
        if (b.firstName.length() < 2 || !NAME.matcher(b.firstName).matches())
            e.put("firstName", "Enter a valid first name (letters only, min 2 characters).");
        if (b.lastName.length() < 2 || !NAME.matcher(b.lastName).matches())
            e.put("lastName", "Enter a valid surname (letters only, min 2 characters).");

        b.idNumber = b.idNumber == null ? "" : b.idNumber.replaceAll("\\s", "");
        b.dob = clean(b.dob, 10);
        b.gender = b.gender == null ? "" : b.gender;

        if (!b.idNumber.isEmpty()) {
            SaId p = parseSaId(b.idNumber);
            if (!p.valid) e.put("idNumber", p.error);
            else {
                if (!b.dob.isEmpty() && !b.dob.equals(p.dob)) e.put("dob", "Date of birth does not match the ID number.");
                if (b.dob.isEmpty()) b.dob = p.dob;
                if (b.gender.isEmpty()) b.gender = p.gender;
                if (existing != null) {
                    for (Beneficiary other : existing) {
                        if (b.idNumber.equals(other.idNumber) && (b.id == null || !b.id.equals(other.id))) {
                            e.put("idNumber", "This ID number is already registered (" + other.fullName() + ").");
                            break;
                        }
                    }
                }
            }
        }

        if (b.dob.isEmpty()) {
            if (!e.containsKey("dob")) e.put("dob", "Date of birth is required when no ID number is supplied.");
        } else if (!DateUtils.isValidISODate(b.dob)) e.put("dob", "Enter a valid date of birth.");
        else if (DateUtils.isFuture(b.dob)) e.put("dob", "Date of birth cannot be in the future.");
        else if (DateUtils.age(b.dob) > 120) e.put("dob", "Date of birth is not plausible.");

        if (!Constants.GENDERS.contains(b.gender)) e.put("gender", "Select a gender.");

        String phoneRaw = b.phone == null ? "" : b.phone.trim();
        b.phone = "";
        if (!phoneRaw.isEmpty()) {
            String p = normalisePhone(phoneRaw);
            if (p == null) e.put("phone", "Enter a valid South African number, e.g. 082 123 4567.");
            else b.phone = p;
        }

        b.address = clean(b.address, 200);
        b.area = clean(b.area, 80);
        if (b.area.length() < 2) e.put("area", "Area / ward is required.");

        if (!Constants.PROGRAMMES.contains(b.programme)) e.put("programme", "Select a programme.");

        b.enrolmentDate = clean(b.enrolmentDate, 10);
        if (!DateUtils.isValidISODate(b.enrolmentDate)) e.put("enrolmentDate", "Enter a valid enrolment date.");
        else if (DateUtils.isFuture(b.enrolmentDate)) e.put("enrolmentDate", "Enrolment date cannot be in the future.");

        if (b.status == null || b.status.isEmpty()) b.status = "active";
        if (!Constants.BENEFICIARY_STATUSES.contains(b.status)) e.put("status", "Invalid status.");

        if (b.assignedChwId == null || b.assignedChwId.isEmpty()) e.put("assignedChwId", "Assign a Community Health Worker.");

        if (!b.consent) e.put("consent", "POPIA consent must be recorded before registering a beneficiary.");
        return e;
    }

    // ---------------------------------------------------------------- visit

    public static Map<String, String> validateVisit(Visit v, boolean isNew) {
        Map<String, String> e = new LinkedHashMap<>();
        if (isEmpty(v.beneficiaryId)) e.put("beneficiaryId", "Select a beneficiary.");
        if (isEmpty(v.chwId)) e.put("chwId", "Select the Community Health Worker.");
        if (isEmpty(v.status)) v.status = "completed";
        if (!Constants.VISIT_STATUSES.contains(v.status)) e.put("status", "Invalid status.");
        v.date = clean(v.date, 10);
        if (!DateUtils.isValidISODate(v.date)) e.put("date", "Enter a valid visit date.");
        v.location = clean(v.location, 150);
        v.notes = cleanMultiline(v.notes, 2000);
        if (v.outcome == null) v.outcome = "";

        if ("completed".equals(v.status)) {
            if (!e.containsKey("date") && DateUtils.isFuture(v.date)) e.put("date", "A completed visit cannot be dated in the future.");
            if (v.location.length() < 2) e.put("location", "Location is required for a completed visit.");
            if (!Constants.VISIT_OUTCOMES.contains(v.outcome)) e.put("outcome", "Select the visit outcome.");
            if (v.notes.length() < 10) e.put("notes", "Visit notes are required (min 10 characters).");
        } else if ("scheduled".equals(v.status) && isNew && !e.containsKey("date") && DateUtils.isPast(v.date)) {
            e.put("date", "A new scheduled visit cannot be in the past.");
        }
        return e;
    }

    // ---------------------------------------------------------------- referral

    public static Map<String, String> validateReferral(Referral r) {
        Map<String, String> e = new LinkedHashMap<>();
        if (isEmpty(r.beneficiaryId)) e.put("beneficiaryId", "Select a beneficiary.");
        if (isEmpty(r.chwId)) e.put("chwId", "Select the Community Health Worker.");
        r.facility = clean(r.facility, 120);
        if (r.facility.length() < 3) e.put("facility", "Enter the receiving health facility.");
        r.reason = cleanMultiline(r.reason, 1000);
        if (r.reason.length() < 5) e.put("reason", "Enter the reason for referral (min 5 characters).");
        if (isEmpty(r.urgency)) r.urgency = "routine";
        if (!Constants.REFERRAL_URGENCY.contains(r.urgency)) e.put("urgency", "Select urgency.");
        r.referralDate = clean(r.referralDate, 10);
        if (!DateUtils.isValidISODate(r.referralDate)) e.put("referralDate", "Enter a valid referral date.");
        else if (DateUtils.isFuture(r.referralDate)) e.put("referralDate", "Referral date cannot be in the future.");
        if (isEmpty(r.status)) r.status = "pending";
        if (!Constants.REFERRAL_STATUSES.contains(r.status)) e.put("status", "Invalid status.");
        r.outcomeNotes = cleanMultiline(r.outcomeNotes, 1000);
        if (("attended".equals(r.status) || "completed".equals(r.status)) && r.outcomeNotes.length() < 5)
            e.put("outcomeNotes", "Record the outcome before marking the referral attended/completed.");
        return e;
    }

    // ---------------------------------------------------------------- password + sign-up

    /** At least 8 characters with an upper-case letter, a lower-case letter and a number (same rule as the web app). */
    public static String passwordError(String pw) {
        if (pw == null || pw.length() < 8) return "Password must be at least 8 characters.";
        if (!pw.matches(".*[a-z].*") || !pw.matches(".*[A-Z].*") || !pw.matches(".*\\d.*"))
            return "Password must contain an upper-case letter, a lower-case letter and a number.";
        return null;
    }

    /** Validate the self sign-up form. Returns field -> error (keys: name, email, phone, role, password, confirm, consent). */
    public static Map<String, String> validateSignup(String name, String email, String phone, String role,
                                                     String password, String confirm, boolean consent) {
        Map<String, String> e = new LinkedHashMap<>();
        String n = clean(name, 80);
        if (n.length() < 3 || !NAME.matcher(n).matches()) e.put("name", "Enter your full name (letters only).");
        if (email == null || !isValidEmail(email)) e.put("email", "Enter a valid email address.");
        if (phone != null && !phone.trim().isEmpty() && normalisePhone(phone) == null)
            e.put("phone", "Enter a valid South African number, e.g. 082 123 4567.");
        if (!"chw".equals(role) && !"coordinator".equals(role)) e.put("role", "Choose Community Health Worker or Programme Coordinator.");
        String pe = passwordError(password);
        if (pe != null) e.put("password", pe);
        if (password == null || !password.equals(confirm)) e.put("confirm", "The passwords do not match.");
        if (!consent) e.put("consent", "Please tick the box to continue.");
        return e;
    }

    // ---------------------------------------------------------------- appointment

    /** Validates AND cleans the appointment (changed in place). */
    public static Map<String, String> validateAppointment(Appointment a, boolean dateChanged) {
        Map<String, String> e = new LinkedHashMap<>();
        if (isEmpty(a.beneficiaryId)) e.put("beneficiaryId", "Select a beneficiary.");
        if (isEmpty(a.chwId)) e.put("chwId", "Select the Community Health Worker.");
        a.datetime = clean(a.datetime, 16);
        if (!DateUtils.isValidDateTime(a.datetime)) e.put("date", "Choose a valid date and time.");
        a.purpose = clean(a.purpose, 150);
        if (a.purpose.length() < 3) e.put("purpose", "Enter the purpose / facility (min 3 characters).");
        if (isEmpty(a.status)) a.status = "booked";
        if (!Constants.APPOINTMENT_STATUSES.contains(a.status)) e.put("status", "Invalid status.");
        if (isEmpty(a.reminderChannel)) a.reminderChannel = "none";
        if (!Constants.REMINDER_CHANNELS.contains(a.reminderChannel)) e.put("reminderChannel", "Invalid reminder option.");
        a.notes = cleanMultiline(a.notes, 1000);
        if (!e.containsKey("date") && dateChanged
                && ("booked".equals(a.status) || "rescheduled".equals(a.status))
                && !DateUtils.isFutureDateTime(a.datetime)) {
            e.put("date", "Bookings and reschedules must be in the future.");
        }
        a.reminderDue = !"none".equals(a.reminderChannel) && !e.containsKey("date") ? DateUtils.reminderDue(a.datetime) : "";
        return e;
    }

    private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }
}
