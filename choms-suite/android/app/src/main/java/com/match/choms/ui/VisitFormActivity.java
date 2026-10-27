package com.match.choms.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputLayout;
import com.match.choms.databinding.ActivityVisitFormBinding;
import com.match.choms.model.Beneficiary;
import com.match.choms.model.User;
import com.match.choms.model.Visit;
import com.match.choms.util.Constants;
import com.match.choms.util.DateUtils;
import com.match.choms.util.Option;
import com.match.choms.util.Resource;
import com.match.choms.viewmodel.VisitViewModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Record (or edit) a visit. */
public class VisitFormActivity extends AppCompatActivity {
    private ActivityVisitFormBinding b;
    private VisitViewModel vm;
    private FormUtils.Dropdown beneficiary, chw, status, outcome;
    private final Map<String, TextInputLayout> fields = new LinkedHashMap<>();
    private final Map<String, Beneficiary> beneficiaryById = new LinkedHashMap<>();
    private Visit editing;
    private User user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        user = SessionGuard.require(this);
        if (user == null) return;

        b = ActivityVisitFormBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        vm = new ViewModelProvider(this).get(VisitViewModel.class);
        b.toolbar.setNavigationOnClickListener(v -> finish());

        fields.put("beneficiaryId", b.tilBeneficiaryId);
        fields.put("chwId", b.tilChwId);
        fields.put("status", b.tilStatus);
        fields.put("date", b.tilDate);
        fields.put("location", b.tilLocation);
        fields.put("outcome", b.tilOutcome);
        fields.put("notes", b.tilNotes);

        beneficiary = new FormUtils.Dropdown(b.acBeneficiaryId);
        chw = new FormUtils.Dropdown(b.acChwId);
        status = new FormUtils.Dropdown(b.acStatus);
        outcome = new FormUtils.Dropdown(b.acOutcome);
        status.setOptions(FormUtils.options(Constants.VISIT_STATUSES));
        outcome.setOptions(FormUtils.options(Constants.VISIT_OUTCOMES));
        FormUtils.bindDatePicker(this, b.etDate);

        b.tilChwId.setVisibility(user.isAdmin() ? View.VISIBLE : View.GONE);

        // choosing a beneficiary pre-selects that beneficiary's CHW (Admin/Coordinator)
        b.acBeneficiaryId.setOnItemClickListener((p, v, pos, id) -> {
            Beneficiary sel = beneficiaryById.get(beneficiary.value());
            if (user.isAdmin() && sel != null && chw.value().isEmpty()) chw.select(sel.assignedChwId);
        });

        vm.loadBeneficiaries();
        vm.getBeneficiaries().observe(this, list -> {
            List<Option> opts = new ArrayList<>();
            beneficiaryById.clear();
            for (Beneficiary x : list) {
                beneficiaryById.put(x.id, x);
                if (!"closed".equals(x.status)) opts.add(new Option(x.id, x.fullName()));
            }
            beneficiary.setOptions(opts);
        });
        vm.getChws().observe(this, list -> {
            List<Option> opts = new ArrayList<>();
            for (User u : list) opts.add(new Option(u.id, u.displayName()));
            chw.setOptions(opts);
        });

        String id = getIntent().getStringExtra(Constants.EXTRA_ID);
        if (id == null) {
            b.toolbar.setTitle("Record visit");
            if (savedInstanceState == null) {
                b.etDate.setText(DateUtils.todayISO());
                status.select("completed");
            }
        } else {
            b.toolbar.setTitle("Edit visit");
            vm.loadOne(id);
        }

        vm.getSingle().observe(this, state -> {
            if (state.status == Resource.Status.SUCCESS && state.data != null) {
                editing = state.data;
                if (savedInstanceState == null) populate(editing);
            } else if (state.status == Resource.Status.ERROR) {
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show();
                finish();
            }
        });

        vm.getFieldErrors().observe(this, errors -> FormUtils.showErrors(errors, fields));

        vm.getSaveState().observe(this, state -> {
            boolean loading = state.status == Resource.Status.LOADING;
            b.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
            b.btnSave.setEnabled(!loading);
            if (state.status == Resource.Status.SUCCESS) {
                Toast.makeText(this, "Visit saved.", Toast.LENGTH_SHORT).show();
                finish();
            } else if (state.status == Resource.Status.ERROR) {
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show();
            }
        });

        b.btnSave.setOnClickListener(v -> save());
        if (!user.canEditRecords()) {                // coordinators can open a record but not change it
            b.btnSave.setVisibility(View.GONE);
            b.toolbar.setTitle("View (read only)");
        }
    }

    private void populate(Visit x) {
        beneficiary.select(x.beneficiaryId);
        chw.select(x.chwId);
        status.select(x.status);
        b.etDate.setText(x.date);
        b.etLocation.setText(x.location);
        outcome.select(x.outcome);
        b.etNotes.setText(x.notes);
    }

    private void save() {
        Visit x = editing != null ? editing : new Visit();
        x.beneficiaryId = beneficiary.value();
        x.chwId = user.isAdmin() ? chw.value() : user.id;
        x.status = status.value();
        x.date = FormUtils.text(b.etDate);
        x.location = FormUtils.text(b.etLocation);
        x.outcome = outcome.value();
        x.notes = FormUtils.text(b.etNotes);
        vm.save(x, beneficiary.label());
    }
}
