package com.match.choms.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputLayout;
import com.match.choms.databinding.ActivityReferralFormBinding;
import com.match.choms.model.Beneficiary;
import com.match.choms.model.Referral;
import com.match.choms.model.User;
import com.match.choms.util.Constants;
import com.match.choms.util.DateUtils;
import com.match.choms.util.Option;
import com.match.choms.util.Resource;
import com.match.choms.viewmodel.ReferralViewModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Create (or edit) a referral to a health facility. */
public class ReferralFormActivity extends AppCompatActivity {
    private ActivityReferralFormBinding b;
    private ReferralViewModel vm;
    private FormUtils.Dropdown beneficiary, chw, urgency, status;
    private final Map<String, TextInputLayout> fields = new LinkedHashMap<>();
    private final Map<String, Beneficiary> beneficiaryById = new LinkedHashMap<>();
    private Referral editing;
    private User user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        user = SessionGuard.require(this);
        if (user == null) return;

        b = ActivityReferralFormBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        vm = new ViewModelProvider(this).get(ReferralViewModel.class);
        b.toolbar.setNavigationOnClickListener(v -> finish());

        fields.put("beneficiaryId", b.tilBeneficiaryId);
        fields.put("chwId", b.tilChwId);
        fields.put("facility", b.tilFacility);
        fields.put("urgency", b.tilUrgency);
        fields.put("referralDate", b.tilReferralDate);
        fields.put("status", b.tilStatus);
        fields.put("reason", b.tilReason);
        fields.put("outcomeNotes", b.tilOutcomeNotes);

        beneficiary = new FormUtils.Dropdown(b.acBeneficiaryId);
        chw = new FormUtils.Dropdown(b.acChwId);
        urgency = new FormUtils.Dropdown(b.acUrgency);
        status = new FormUtils.Dropdown(b.acStatus);
        urgency.setOptions(FormUtils.options(Constants.REFERRAL_URGENCY));
        status.setOptions(FormUtils.options(Constants.REFERRAL_STATUSES));
        FormUtils.bindDatePicker(this, b.etReferralDate);

        b.tilChwId.setVisibility(user.isAdmin() ? View.VISIBLE : View.GONE);

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
            b.toolbar.setTitle("New referral");
            if (savedInstanceState == null) {
                b.etReferralDate.setText(DateUtils.todayISO());
                urgency.select("routine");
                status.select("pending");
            }
        } else {
            b.toolbar.setTitle("Edit referral");
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
                Toast.makeText(this, "Referral saved.", Toast.LENGTH_SHORT).show();
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

    private void populate(Referral x) {
        beneficiary.select(x.beneficiaryId);
        chw.select(x.chwId);
        b.etFacility.setText(x.facility);
        urgency.select(x.urgency);
        b.etReferralDate.setText(x.referralDate);
        status.select(x.status);
        b.etReason.setText(x.reason);
        b.etOutcomeNotes.setText(x.outcomeNotes);
    }

    private void save() {
        Referral x = editing != null ? editing : new Referral();
        x.beneficiaryId = beneficiary.value();
        x.chwId = user.isAdmin() ? chw.value() : user.id;
        x.facility = FormUtils.text(b.etFacility);
        x.urgency = urgency.value();
        x.referralDate = FormUtils.text(b.etReferralDate);
        x.status = status.value();
        x.reason = FormUtils.text(b.etReason);
        x.outcomeNotes = FormUtils.text(b.etOutcomeNotes);
        vm.save(x, beneficiary.label());
    }
}
