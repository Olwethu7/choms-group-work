package com.match.choms.util;

import java.util.Arrays;
import java.util.List;

/** Names shared with the web app so both apps read/write the SAME Firestore data. */
public final class Constants {
    private Constants() { }

    // Firestore collections
    public static final String COL_USERS = "users";
    public static final String COL_BENEFICIARIES = "beneficiaries";
    public static final String COL_VISITS = "visits";
    public static final String COL_REFERRALS = "referrals";
    public static final String COL_APPOINTMENTS = "appointments";
    public static final String COL_AUDIT = "auditLogs";

    // Roles (stored lowercase in users/{uid}.role). New accounts start as pending; an admin approves them as chw / coordinator / admin.
    public static final String ROLE_PENDING = "pending";
    public static final String ROLE_CHW = "chw";
    public static final String ROLE_COORDINATOR = "coordinator";
    public static final String ROLE_ADMIN = "admin";
    public static final List<String> ROLES = Arrays.asList(ROLE_PENDING, ROLE_CHW, ROLE_COORDINATOR, ROLE_ADMIN);
    /** Roles an approved account can have. */
    public static final List<String> ACTIVE_ROLES = Arrays.asList(ROLE_CHW, ROLE_COORDINATOR, ROLE_ADMIN);
    /** What a person may ask to be when signing up (an admin decides). */
    public static final List<String> SIGNUP_ROLES = Arrays.asList(ROLE_CHW, ROLE_COORDINATOR);

    // Cloud Functions region (same as Firestore, see functions/index.js)
    public static final String FUNCTIONS_REGION = "africa-south1";

    // Drop-down values (identical to js/utils.js)
    public static final List<String> GENDERS = Arrays.asList("female", "male", "other");
    public static final List<String> PROGRAMMES = Arrays.asList("HIV/TB", "Maternal Health", "Child Health", "Adolescent Health");
    public static final List<String> BENEFICIARY_STATUSES = Arrays.asList("active", "closed");
    public static final List<String> VISIT_STATUSES = Arrays.asList("scheduled", "completed", "cancelled");
    public static final List<String> VISIT_OUTCOMES = Arrays.asList(
            "Stable - no action needed", "Follow-up visit needed", "Referred to facility",
            "Client not found", "Client refused service");
    public static final List<String> REFERRAL_URGENCY = Arrays.asList("routine", "urgent", "emergency");
    public static final List<String> REFERRAL_STATUSES = Arrays.asList("pending", "attended", "completed", "missed", "cancelled");

    public static final List<String> APPOINTMENT_STATUSES = Arrays.asList("booked", "rescheduled", "attended", "missed", "cancelled");
    public static final List<String> REMINDER_CHANNELS = Arrays.asList("none", "sms", "email");

    // Intent extra keys
    public static final String EXTRA_ID = "extra_id";
    public static final String EXTRA_NOTICE = "extra_notice";
}
