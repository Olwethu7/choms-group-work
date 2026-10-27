package com.match.choms.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputLayout;
import com.match.choms.databinding.ActivityBeneficiaryFormBinding;
import com.match.choms.model.Beneficiary;
import com.match.choms.model.User;
import com.match.choms.util.Constants;
import com.match.choms.util.DateUtils;
import com.match.choms.util.Option;
import com.match.choms.util.Resource;
import com.match.choms.util.Validators;
import com.match.choms.viewmodel.BeneficiaryViewModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Add / edit a beneficiary. The same screen is used for both: an id extra means "edit". */
public class BeneficiaryFormActivity extends AppCompatActivity {
    private ActivityBeneficiaryFormBinding b;
    private BeneficiaryViewModel vm;
    private FormUtils.Dropdown gender, programme, status, chw;
    private final Map<String, TextInputLayout> fields = new LinkedHashMap<>();
    private Beneficiary editing;          // null when registering a new beneficiary
    private User user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        user = SessionGuard.require(this);
        if (user == null) return;

        b = ActivityBeneficiaryFormBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        vm = new ViewModelProvider(this).get(BeneficiaryViewModel.class);
        b.toolbar.setNavigationOnClickListener(v -> finish());

        // map: field name (same as the model / validator keys) -> its input layout
        fields.put("firstName", b.tilFirstName);
        fields.put("lastName", b.tilLastName);
        fields.put("idNumber", b.tilIdNumber);
        fields.put("dob", b.tilDob);
        fields.put("gender", b.tilGender);
        fields.put("phone", b.tilPhone);
        fields.put("area", b.tilArea);
        fields.put("programme", b.tilProgramme);
        fields.put("enrolmentDate", b.tilEnrolmentDate);
        fields.put("assignedChwId", b.tilAssignedChwId);
        fields.put("status", b.tilStatus);
        fields.put("address", b.tilAddress);

        gender = new FormUtils.Dropdown(b.acGender);
        programme = new FormUtils.Dropdown(b.acProgramme);
        status = new FormUtils.Dropdown(b.acStatus);
        chw = new FormUtils.Dropdown(b.acAssignedChwId);
        gender.setOptions(FormUtils.options(Constants.GENDERS));
        programme.setOptions(FormUtils.options(Constants.PROGRAMMES));
        status.setOptions(FormUtils.options(Constants.BENEFICIARY_STATUSES));
        FormUtils.bindDatePicker(this, b.etDob);
        FormUtils.bindDatePicker(this, b.etEnrolmentDate);

        // Only Admin / Coordinator choose which CHW owns the client; a CHW's own UID is used automatically.
        b.tilAssignedChwId.setVisibility(user.isAdmin() ? View.VISIBLE : View.GONE);
        if (user.isAdmin()) vm.loadChws();
        vm.getChws().observe(this, list -> {
            List<Option> opts = new ArrayList<>();
            for (User u : list) opts.add(new Option(u.id, u.displayName()));
            chw.setOptions(opts);
        });

        // Typing a valid SA ID number fills in date of birth and gender automatically
        b.etIdNumber.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable s) {
                Validators.SaId p = Validators.parseSaId(s.toString());
                if (p.valid) {
                    if (FormUtils.text(b.etDob).isEmpty()) b.etDob.setText(p.dob);
                    if (gender.value().isEmpty()) gender.select(p.gender);
                }
            }
        });

        String id = getIntent().getStringExtra(Constants.EXTRA_ID);
        if (id == null) {
            b.toolbar.setTitle(user.isAdmin() ? "Register beneficiary" : "Register client");
            if (savedInstanceState == null) {
                b.etEnrolmentDate.setText(DateUtils.todayISO());
                status.select("active");
            }
        } else {
            b.toolbar.setTitle("Edit beneficiary");
            vm.loadOne(id);
        }

        vm.getSingle().observe(this, state -> {
            if (state.status == Resource.Status.SUCCESS && state.data != null) {
                editing = state.data;
                if (savedInstanceState == null) populate(editing);
                b.btnDelete.setVisibility(user.isAdmin() ? View.VISIBLE : View.GONE);
            } else if (state.status == Resource.Status.ERROR) {
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show();
                finish();
            }
        });

        vm.getFieldErrors().observe(this, errors -> {
            FormUtils.showErrors(errors, fields);
            b.tvConsentError.setText(errors.get("consent"));
            b.tvConsentError.setVisibility(errors.containsKey("consent") ? View.VISIBLE : View.GONE);
        });

        vm.getSaveState().observe(this, state -> {
            boolean loading = state.status == Resource.Status.LOADING;
            b.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
            b.btnSave.setEnabled(!loading);
            if (state.status == Resource.Status.SUCCESS) {
                Toast.makeText(this, "Beneficiary saved.", Toast.LENGTH_SHORT).show();
                finish();
            } else if (state.status == Resource.Status.ERROR) {
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show();
            }
        });

        vm.getDeleteState().observe(this, state -> {
            if (state.status == Resource.Status.SUCCESS) {
                Toast.makeText(this, "Beneficiary deleted.", Toast.LENGTH_SHORT).show();
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
        b.btnDelete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Delete beneficiary")
                .setMessage("Delete " + (editing == null ? "this beneficiary" : editing.fullName()) + "? This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> { if (editing != null) vm.delete(editing); })
                .show());
    }

    private void populate(Beneficiary x) {
        b.etFirstName.setText(x.firstName);
        b.etLastName.setText(x.lastName);
        b.etIdNumber.setText(x.idNumber);
        b.etDob.setText(x.dob);
        gender.select(x.gender);
        b.etPhone.setText(x.phone);
        b.etArea.setText(x.area);
        programme.select(x.programme);
        b.etEnrolmentDate.setText(x.enrolmentDate);
        chw.select(x.assignedChwId);
        status.select(x.status);
        b.etAddress.setText(x.address);
        b.cbConsent.setChecked(x.consent);
    }

    /** Read the form into a model object and let the ViewModel validate and save it. */
    private void save() {
        Beneficiary x = editing != null ? editing : new Beneficiary();
        x.firstName = FormUtils.text(b.etFirstName);
        x.lastName = FormUtils.text(b.etLastName);
        x.idNumber = FormUtils.text(b.etIdNumber);
        x.dob = FormUtils.text(b.etDob);
        x.gender = gender.value();
        x.phone = FormUtils.text(b.etPhone);
        x.area = FormUtils.text(b.etArea);
        x.programme = programme.value();
        x.enrolmentDate = FormUtils.text(b.etEnrolmentDate);
        x.status = status.value();
        x.address = FormUtils.text(b.etAddress);
        x.consent = b.cbConsent.isChecked();
        x.assignedChwId = user.isAdmin() ? chw.value() : user.id;
        vm.save(x);
    }
}
