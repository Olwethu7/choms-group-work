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
import com.match.choms.model.User;
import com.match.choms.model.Visit;
import com.match.choms.util.Constants;
import com.match.choms.util.DateUtils;
import com.match.choms.util.Resource;
import com.match.choms.viewmodel.VisitViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** List of visits. Rows show the beneficiary's name, looked up from the beneficiaries list. */
public class VisitListActivity extends AppCompatActivity {
    private ActivityListBinding binding;
    private VisitViewModel vm;
    private RowAdapter adapter;
    private List<Visit> visits = new ArrayList<>();
    private final Map<String, String> names = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        User user = SessionGuard.require(this);
        if (user == null) return;

        binding = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = new ViewModelProvider(this).get(VisitViewModel.class);

        binding.toolbar.setTitle(user.isStaff() ? "Visits" : "My Visits");
        binding.fab.setVisibility(user.canEditRecords() ? View.VISIBLE : View.GONE);   // coordinators only view
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.etSearch.setHint("Search name, location, outcome...");
        binding.tvEmpty.setText("No visits recorded yet.\nTap + to record one.");

        adapter = new RowAdapter(row -> {
            Intent i = new Intent(this, VisitFormActivity.class);
            i.putExtra(Constants.EXTRA_ID, row.id);
            startActivity(i);
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        binding.fab.setOnClickListener(v -> startActivity(new Intent(this, VisitFormActivity.class)));
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
                visits = state.data;
                render();
            }
        });
        vm.getBeneficiaries().observe(this, list -> {
            names.clear();
            for (Beneficiary b : list) names.put(b.id, b.fullName());
            render();   // names arrived: refresh the rows
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (vm != null) vm.load();
    }

    private void render() {
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Visit v : visits) {
            String name = names.containsKey(v.beneficiaryId) ? names.get(v.beneficiaryId) : "Unknown beneficiary";
            RowAdapter.Row r = new RowAdapter.Row();
            r.id = v.id;
            r.title = name;
            r.subtitle = DateUtils.display(v.date) + (v.location == null || v.location.isEmpty() ? "" : "  |  " + v.location);
            r.detail = v.outcome == null ? "" : v.outcome;
            r.badge = v.status == null ? "" : v.status;
            r.searchText = name + " " + nz(v.location) + " " + nz(v.outcome) + " " + nz(v.notes) + " " + nz(v.status);
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
