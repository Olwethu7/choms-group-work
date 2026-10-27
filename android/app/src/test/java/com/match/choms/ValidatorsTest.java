package com.match.choms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.match.choms.model.Appointment;
import com.match.choms.model.Beneficiary;
import com.match.choms.model.Referral;
import com.match.choms.model.Visit;
import com.match.choms.util.DateUtils;
import com.match.choms.util.Validators;

import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

/** Unit tests for the form validation rules (pure Java - no Android device needed). */
public class ValidatorsTest {

    private Beneficiary validBeneficiary() {
        Beneficiary b = new Beneficiary();
        b.firstName = "Thandi"; b.lastName = "Nkosi"; b.idNumber = "7806155123089";
        b.dob = ""; b.gender = ""; b.phone = "0821234567"; b.area = "Umlazi";
        b.programme = "Maternal Health"; b.enrolmentDate = DateUtils.todayISO();
        b.assignedChwId = "chw1"; b.consent = true;
        return b;
    }

    @Test public void luhnChecksum() {
        assertTrue(Validators.luhnValid("1503120123080"));
        assertFalse(Validators.luhnValid("1503120123081"));
    }

    @Test public void saIdGivesDobAndGender() {
        Validators.SaId id = Validators.parseSaId("7806155123089");
        assertTrue(id.valid);
        assertEquals("1978-06-15", id.dob);
        assertEquals("male", id.gender);
        assertFalse(Validators.parseSaId("123").valid);
    }

    @Test public void phoneIsNormalised() {
        assertEquals("+27821234567", Validators.normalisePhone("082 123 4567"));
        assertNull(Validators.normalisePhone("12345"));
    }

    @Test public void loginRequiresEmailAndPassword() {
        assertEquals(2, Validators.validateLogin("", "").size());
        assertTrue(Validators.validateLogin("a@b.co", "x").isEmpty());
    }

    @Test public void validBeneficiaryPassesAndIsCleaned() {
        Beneficiary b = validBeneficiary();
        assertTrue(Validators.validateBeneficiary(b, null).isEmpty());
        assertEquals("1978-06-15", b.dob);
        assertEquals("+27821234567", b.phone);
    }

    @Test public void consentIsRequired() {
        Beneficiary b = validBeneficiary();
        b.consent = false;
        assertTrue(Validators.validateBeneficiary(b, null).containsKey("consent"));
    }

    @Test public void duplicateIdIsRejected() {
        Beneficiary other = new Beneficiary();
        other.id = "x"; other.idNumber = "7806155123089"; other.firstName = "A"; other.lastName = "B";
        Map<String, String> e = Validators.validateBeneficiary(validBeneficiary(), Arrays.asList(other));
        assertTrue(e.containsKey("idNumber"));
    }

    @Test public void completedVisitNeedsNotesAndOutcome() {
        Visit v = new Visit();
        v.beneficiaryId = "b"; v.chwId = "c"; v.status = "completed"; v.date = DateUtils.todayISO();
        v.location = "Home";
        Map<String, String> e = Validators.validateVisit(v, true);
        assertTrue(e.containsKey("outcome"));
        assertTrue(e.containsKey("notes"));
    }

    @Test public void referralCompletedNeedsOutcome() {
        Referral r = new Referral();
        r.beneficiaryId = "b"; r.chwId = "c"; r.facility = "Clinic A"; r.reason = "Needs check-up";
        r.referralDate = DateUtils.todayISO(); r.status = "completed";
        assertTrue(Validators.validateReferral(r).containsKey("outcomeNotes"));
    }

    @Test public void impossibleDateRejected() {
        assertFalse(DateUtils.isValidISODate("2026-02-30"));
    }

    // ---------------------------------------------------------------- sign-up

    private Map<String, String> signup(String name, String email, String phone, String role, String pw, String confirm, boolean consent) {
        return Validators.validateSignup(name, email, phone, role, pw, confirm, consent);
    }

    @Test public void validSignupHasNoErrors() {
        assertTrue(signup("Lindiwe Zulu", "lindiwe@example.org", "082 555 1234", "coordinator", "Strong123", "Strong123", true).isEmpty());
    }

    @Test public void signupPhoneIsOptional() {
        assertTrue(signup("Thandi Nkosi", "t@example.org", "", "chw", "Strong123", "Strong123", true).isEmpty());
    }

    @Test public void signupRejectsEmptyForm() {
        Map<String, String> e = signup("", "", "", "", "", "", false);
        assertTrue(e.containsKey("name") && e.containsKey("email") && e.containsKey("role")
                && e.containsKey("password") && e.containsKey("consent"));
    }

    @Test public void signupRejectsWeakAndMismatchedPasswords() {
        assertTrue(signup("Thandi Nkosi", "t@example.org", "", "chw", "weak", "weak", true).containsKey("password"));
        assertTrue(signup("Thandi Nkosi", "t@example.org", "", "chw", "alllowercase1", "alllowercase1", true).containsKey("password"));
        assertTrue(signup("Thandi Nkosi", "t@example.org", "", "chw", "Strong123", "Strong124", true).containsKey("confirm"));
    }

    @Test public void signupCannotRequestAdminRole() {
        assertTrue(signup("Thandi Nkosi", "t@example.org", "", "admin", "Strong123", "Strong123", true).containsKey("role"));
    }

    @Test public void rolesControlAccess() {
        com.match.choms.model.User u = new com.match.choms.model.User();
        u.role = "pending";
        assertTrue(u.isPending() && !u.canSignIn() && !u.canEditRecords());
        u.role = "chw";
        assertTrue(u.canSignIn() && u.canEditRecords() && !u.isStaff());
        u.role = "coordinator";
        assertTrue(u.canSignIn() && u.isStaff() && !u.canEditRecords() && !u.isAdmin());
        u.role = "admin";
        assertTrue(u.canSignIn() && u.isStaff() && u.canEditRecords() && u.isAdmin());
        u.status = "deactivated";
        assertTrue(!u.canSignIn());
        u.status = "active";
        u.role = "user";   // not a valid role
        assertTrue(!u.canSignIn());
    }

    @Test public void signupRejectsBadPhoneAndName() {
        assertTrue(signup("Thandi Nkosi", "t@example.org", "12345", "chw", "Strong123", "Strong123", true).containsKey("phone"));
        assertTrue(signup("R2D2 <script>", "t@example.org", "", "chw", "Strong123", "Strong123", true).containsKey("name"));
    }

    // ---------------------------------------------------------------- appointments

    private Appointment appt(String dt) {
        Appointment a = new Appointment();
        a.beneficiaryId = "b"; a.chwId = "c"; a.purpose = "Clinic check-up"; a.datetime = dt;
        return a;
    }

    @Test public void appointmentInThePastIsRejectedWhenBooking() {
        assertTrue(Validators.validateAppointment(appt("2020-01-01T09:00"), true).containsKey("date"));
    }

    @Test public void futureAppointmentIsValidAndGetsReminderDue() {
        Appointment a = appt("2099-05-04T09:30");
        assertTrue(Validators.validateAppointment(a, true).isEmpty());
        assertEquals("2099-05-03T09:30", a.reminderDue);   // 24 hours before
    }

    @Test public void oldAppointmentCanBeMarkedAttendedWithoutChangingDate() {
        Appointment a = appt("2020-01-01T09:00");
        a.status = "attended";
        assertTrue(Validators.validateAppointment(a, false).isEmpty());
    }

    @Test public void appointmentDateTimeFormatIsChecked() {
        assertTrue(DateUtils.isValidDateTime("2026-10-27T14:30"));
        assertFalse(DateUtils.isValidDateTime("2026-10-27T25:30"));
        assertFalse(DateUtils.isValidDateTime("2026-02-30T10:00"));
        assertFalse(DateUtils.isValidDateTime("27/10/2026 14:30"));
    }
}
