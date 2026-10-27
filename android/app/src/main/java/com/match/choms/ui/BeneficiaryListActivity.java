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
import com.match.choms.util.Constants;
import com.match.choms.util.Resource;
import com.match.choms.viewmodel.BeneficiaryViewModel;

import java.util.ArrayList;
import java.util.List;

/** List of beneficiaries (CHW: own clients only, Admin/Coordinator: everyone) with search. */
public class BeneficiaryListActivity extends AppCompatActivity {
    private ActivityListBinding binding;
    private BeneficiaryViewModel vm;
    private RowAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        User user = SessionGuard.require(this);
        if (user == null) return;

        binding = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = new ViewModelProvider(this).get(BeneficiaryViewModel.class);

        binding.toolbar.setTitle(user.isStaff() ? "Beneficiaries" : "My Clients");
        binding.fab.setVisibility(user.canEditRecords() ? View.VISIBLE : View.GONE);   // coordinators only view
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.etSearch.setHint("Search name, ID, area, programme...");
        binding.tvEmpty.setText("No beneficiaries yet.\nTap + to register one.");

        adapter = new RowAdapter(row -> {
            Intent i = new Intent(this, BeneficiaryFormActivity.class);
            i.putExtra(Constants.EXTRA_ID, row.id);
            startActivity(i);
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        binding.fab.setOnClickListener(v -> startActivity(new Intent(this, BeneficiaryFormActivity.class)));
        binding.swipe.setOnRefreshListener(() -> vm.load());
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                adapter.filter(s.toString());
                updateEmpty();
            }
        });

        vm.getList().observe(this, state -> {
            binding.swipe.setRefreshing(state.status == Resource.Status.LOADING);
            if (state.status == Resource.Status.ERROR) {
                binding.tvError.setText(state.message);
                binding.tvError.setVisibility(View.VISIBLE);
            } else if (state.status == Resource.Status.SUCCESS && state.data != null) {
                binding.tvError.setVisibility(View.GONE);
                adapter.setRows(toRows(state.data), String.valueOf(binding.etSearch.getText()));
                updateEmpty();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (vm != null) vm.load();   // refresh after returning from the form
    }

    private List<RowAdapter.Row> toRows(List<Beneficiary> data) {
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Beneficiary b : data) {
            RowAdapter.Row r = new RowAdapter.Row();
            r.id = b.id;
            r.title = b.fullName();
            r.subtitle = (b.ref == null ? "" : b.ref + "  |  ") + nz(b.programme);
            r.detail = nz(b.area) + (b.phone == null || b.phone.isEmpty() ? "" : "  |  " + b.phone);
            r.badge = nz(b.status);
            r.searchText = b.fullName() + " " + nz(b.ref) + " " + nz(b.idNumber) + " " + nz(b.area) + " " + nz(b.programme) + " " + nz(b.phone);
            rows.add(r);
        }
        return rows;
    }

    private void updateEmpty() {
        binding.tvEmpty.setVisibility(adapter.shownCount() == 0 && binding.tvError.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
