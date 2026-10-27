package com.match.choms.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.match.choms.databinding.ActivityReportsBinding;
import com.match.choms.model.DashboardStats;
import com.match.choms.model.User;
import com.match.choms.util.Resource;
import com.match.choms.viewmodel.DashboardViewModel;

import java.util.Map;

/** Summary report for Admin and Coordinator (CHWs are blocked from this screen). */
public class ReportsActivity extends AppCompatActivity {
    private ActivityReportsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        User user = SessionGuard.require(this);
        if (user == null) return;
        if (!user.isStaff()) {                      // role check: reports are for admins and coordinators
            android.widget.Toast.makeText(this, "You do not have access to reports.", android.widget.Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        binding = ActivityReportsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        DashboardViewModel vm = new ViewModelProvider(this).get(DashboardViewModel.class);
        vm.getStats().observe(this, state -> {
            binding.progress.setVisibility(state.status == Resource.Status.LOADING ? View.VISIBLE : View.GONE);
            if (state.status == Resource.Status.ERROR) {
                binding.tvError.setText(state.message);
                binding.tvError.setVisibility(View.VISIBLE);
            } else if (state.status == Resource.Status.SUCCESS && state.data != null) {
                binding.tvError.setVisibility(View.GONE);
                render(state.data);
            }
        });
        vm.load();
    }

    private void render(DashboardStats s) {
        LinearLayout box = binding.llReport;
        box.removeAllViews();
        heading(box, "Overview");
        row(box, "Beneficiaries (total)", s.beneficiaries);
        row(box, "Beneficiaries (active)", s.activeBeneficiaries);
        row(box, "Visits (total)", s.visitsTotal);
        row(box, "Visits completed this month", s.visitsThisMonth);
        row(box, "Visits scheduled", s.visitsScheduled);

        heading(box, "Referrals");
        row(box, "Total referrals", s.referralsTotal);
        row(box, "Pending", s.referralsPending);
        row(box, "Pending and urgent/emergency", s.referralsUrgent);
        row(box, "Attended / completed", s.referralsCompleted);
        row(box, "Missed", s.missedReferrals);
        if (s.referralsTotal > 0) {
            int rate = Math.round(100f * s.referralsCompleted / s.referralsTotal);
            row(box, "Completion rate", rate + "%");
        }

        heading(box, "Beneficiaries by programme");
        if (s.byProgramme.isEmpty()) row(box, "No data yet", "");
        for (Map.Entry<String, Integer> e : s.byProgramme.entrySet()) row(box, e.getKey(), e.getValue());

        heading(box, "Completed visits by outcome");
        if (s.byOutcome.isEmpty()) row(box, "No data yet", "");
        for (Map.Entry<String, Integer> e : s.byOutcome.entrySet()) row(box, e.getKey(), e.getValue());
    }

    private void heading(LinearLayout box, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16f);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(getColor(com.match.choms.R.color.brand_dark));
        t.setPadding(0, dp(20), 0, dp(6));
        box.addView(t);
    }

    private void row(LinearLayout box, String label, Object value) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(6), 0, dp(6));
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(getColor(com.match.choms.R.color.text_primary));
        l.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(this);
        v.setText(String.valueOf(value));
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setTextColor(getColor(com.match.choms.R.color.text_primary));
        r.addView(l);
        r.addView(v);
        box.addView(r);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
