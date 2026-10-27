package com.match.choms.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.match.choms.databinding.ActivityListBinding;
import com.match.choms.model.Beneficiary;
import com.match.choms.model.Referral;
import com.match.choms.model.User;
import com.match.choms.util.Constants;
import com.match.choms.util.DateUtils;
import com.match.choms.util.Resource;
import com.match.choms.viewmodel.ReferralViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** List of referrals to health facilities. */
public class ReferralListActivity extends AppCompatActivity {
    private ActivityListBinding binding;
    private ReferralViewModel vm;
    private RowAdapter adapter;
    private List<Referral> referrals = new ArrayList<>();
    private final Map<String, String> names = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        User user = SessionGuard.require(this);
        if (user == null) return;

        binding = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = new ViewModelProvider(this).get(ReferralViewModel.class);

        binding.toolbar.setTitle("Referrals");
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.etSearch.setHint("Search name, facility, reason...");
        binding.tvEmpty.setText("No referrals yet.\nTap + to create one.");

        adapter = new RowAdapter(row -> {
            Intent i = new Intent(this, ReferralFormActivity.class);
            i.putExtra(Constants.EXTRA_ID, row.id);
            startActivity(i);
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        binding.fab.setVisibility(user.canEditRecords() ? View.VISIBLE : View.GONE);   // coordinators only view
        binding.fab.setOnClickListener(v -> startActivity(new Intent(this, ReferralFormActivity.class)));
        binding.swipe.setOnRefreshListener(() -> vm.load());
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { adapter.filter(s.toString()); updateEmpty(); }
        });

        vm.getList().observe(this, state -> {
            binding.swipe.setRefreshing(state.status == Resource.Status.LOADING);
            if (state.status == Resource.Status.ERROR) {
                binding.tvError.setText(state.message);
                binding.tvError.setVisibility(View.VISIBLE);
            } else if (state.status == Resource.Status.SUCCESS && state.data != null) {
                binding.tvError.setVisibility(View.GONE);
                referrals = state.data;
                render();
            }
        });
        vm.getBeneficiaries().observe(this, list -> {
            names.clear();
            for (Beneficiary b : list) names.put(b.id, b.fullName());
            render();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (vm != null) vm.load();
    }

    private void render() {
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Referral ref : referrals) {
            String name = names.containsKey(ref.beneficiaryId) ? names.get(ref.beneficiaryId) : "Unknown beneficiary";
            RowAdapter.Row r = new RowAdapter.Row();
            r.id = ref.id;
            r.title = name;
            r.subtitle = "To: " + nz(ref.facility);
            r.detail = DateUtils.display(ref.referralDate) + "  |  Urgency: " + nz(ref.urgency);
            // urgent/emergency referrals that are still waiting stand out in the badge
            boolean open = "pending".equals(ref.status);
            r.badge = open && ("urgent".equals(ref.urgency) || "emergency".equals(ref.urgency)) ? ref.urgency : nz(ref.status);
            r.searchText = name + " " + nz(ref.facility) + " " + nz(ref.reason) + " " + nz(ref.urgency) + " " + nz(ref.status);
            rows.add(r);
        }
        adapter.setRows(rows, String.valueOf(binding.etSearch.getText()));
        updateEmpty();
    }

    private void updateEmpty() {
        binding.tvEmpty.setVisibility(adapter.shownCount() == 0 && binding.tvError.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
