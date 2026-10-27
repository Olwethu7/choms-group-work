package com.match.choms.model;

/** Numbers shown on the dashboard and the reports screen. */
public class DashboardStats {
    public int beneficiaries;
    public int activeBeneficiaries;
    public int visitsTotal;
    public int visitsThisMonth;
    public int visitsScheduled;
    public int referralsTotal;
    public int referralsPending;
    public int referralsUrgent;
    public int referralsCompleted;
    public int missedReferrals;
    public int appointmentsUpcoming;
    public int appointmentsMissed;
    /** Accounts waiting for approval (administrators only). */
    public int pendingAccounts;
    /** Beneficiaries per programme, e.g. "Maternal Health" -> 12 */
    public java.util.Map<String, Integer> byProgramme = new java.util.LinkedHashMap<>();
    /** Completed visits per outcome. */
    public java.util.Map<String, Integer> byOutcome = new java.util.LinkedHashMap<>();
}
