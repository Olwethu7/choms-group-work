package com.match.choms.repository;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.QuerySnapshot;
import com.match.choms.model.Appointment;
import com.match.choms.model.Beneficiary;
import com.match.choms.model.DashboardStats;
import com.match.choms.model.Referral;
import com.match.choms.model.User;
import com.match.choms.model.Visit;
import com.match.choms.util.ErrorMapper;

import java.util.List;
import java.util.Map;

/** Builds the dashboard / report numbers from the three core collections (role-scoped). */
public class DashboardRepository extends BaseRepository {
    private final BeneficiaryRepository beneficiaries = new BeneficiaryRepository();
    private final VisitRepository visits = new VisitRepository();
    private final ReferralRepository referrals = new ReferralRepository();
    private final AppointmentRepository appointments = new AppointmentRepository();

    public void loadStats(User user, RepoCallback<DashboardStats> cb) {
        Task<QuerySnapshot> tb = beneficiaries.queryFor(user).get();
        Task<QuerySnapshot> tv = visits.queryFor(user).get();
        Task<QuerySnapshot> tr = referrals.queryFor(user).get();
        Task<QuerySnapshot> ta = appointments.queryFor(user).get();
        // only administrators may list users (rules), so only they ask for the pending accounts
        Task<QuerySnapshot> tu = user.isAdmin()
                ? db.collection(com.match.choms.util.Constants.COL_USERS).whereEqualTo("role", com.match.choms.util.Constants.ROLE_PENDING).get()
                : Tasks.<QuerySnapshot>forResult(null);

        Tasks.whenAllSuccess(tb, tv, tr, ta, tu)
                .addOnSuccessListener(results -> {
                    List<Beneficiary> bens = toList((QuerySnapshot) results.get(0), Beneficiary.class);
                    List<Visit> vis = toList((QuerySnapshot) results.get(1), Visit.class);
                    List<Referral> refs = toList((QuerySnapshot) results.get(2), Referral.class);
                    List<Appointment> appts = toList((QuerySnapshot) results.get(3), Appointment.class);
                    DashboardStats stats = compute(bens, vis, refs);
                    addAppointments(stats, appts);
                    QuerySnapshot pending = (QuerySnapshot) results.get(4);
                    stats.pendingAccounts = pending == null ? 0 : pending.size();
                    cb.onSuccess(stats);
                })
                .addOnFailureListener(e -> cb.onError(ErrorMapper.friendly(e)));
    }

    /** Pure calculation (kept separate so it could be unit tested). */
    static DashboardStats compute(List<Beneficiary> bens, List<Visit> vis, List<Referral> refs) {
        DashboardStats s = new DashboardStats();
        String month = com.match.choms.util.DateUtils.todayISO().substring(0, 7);

        s.beneficiaries = bens.size();
        for (Beneficiary b : bens) {
            if ("active".equals(b.status)) s.activeBeneficiaries++;
            String p = b.programme == null ? "Unknown" : b.programme;
            increment(s.byProgramme, p);
        }
        s.visitsTotal = vis.size();
        for (Visit v : vis) {
            if ("scheduled".equals(v.status)) s.visitsScheduled++;
            if ("completed".equals(v.status)) {
                if (v.date != null && v.date.startsWith(month)) s.visitsThisMonth++;
                if (v.outcome != null && !v.outcome.isEmpty()) increment(s.byOutcome, v.outcome);
            }
        }
        s.referralsTotal = refs.size();
        for (Referral r : refs) {
            if ("pending".equals(r.status)) {
                s.referralsPending++;
                if ("urgent".equals(r.urgency) || "emergency".equals(r.urgency)) s.referralsUrgent++;
            }
            if ("completed".equals(r.status) || "attended".equals(r.status)) s.referralsCompleted++;
            if ("missed".equals(r.status)) s.missedReferrals++;
        }
        return s;
    }

    /** Upcoming = booked/rescheduled and still in the future; missed = marked as missed. */
    static void addAppointments(DashboardStats s, List<Appointment> appts) {
        String now = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US).format(new java.util.Date());
        for (Appointment a : appts) {
            boolean open = "booked".equals(a.status) || "rescheduled".equals(a.status);
            if (open && a.datetime != null && a.datetime.compareTo(now) >= 0) s.appointmentsUpcoming++;
            if ("missed".equals(a.status)) s.appointmentsMissed++;
        }
    }

    private static void increment(Map<String, Integer> map, String key) {
        Integer n = map.get(key);
        map.put(key, n == null ? 1 : n + 1);
    }
}
