package com.match.choms.ui;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputLayout;
import com.match.choms.databinding.ActivityAppointmentFormBinding;
import com.match.choms.model.Appointment;
import com.match.choms.model.Beneficiary;
import com.match.choms.model.User;
import com.match.choms.util.Constants;
import com.match.choms.util.DateUtils;
import com.match.choms.util.Option;
import com.match.choms.util.Resource;
import com.match.choms.viewmodel.AppointmentViewModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Book (or reschedule / update) an appointment. Changing the date or time of a booked appointment marks it as rescheduled. */
public class AppointmentFormActivity extends AppCompatActivity {
    private ActivityAppointmentFormBinding b;
    private AppointmentViewModel vm;
    private FormUtils.Dropdown beneficiary, chw, status, reminder;
    private final Map<String, TextInputLayout> fields = new LinkedHashMap<>();
    private final Map<String, Beneficiary> beneficiaryById = new LinkedHashMap<>();
    private Appointment editing;
    private String originalDateTime;
    private User user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        user = SessionGuard.require(this);
        if (user == null) return;

        b = ActivityAppointmentFormBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        vm = new ViewModelProvider(this).get(AppointmentViewModel.class);
        b.toolbar.setNavigationOnClickListener(v -> finish());

        fields.put("beneficiaryId", b.tilBeneficiaryId);
        fields.put("chwId", b.tilChwId);
        fields.put("purpose", b.tilPurpose);
        fields.put("date", b.tilDate);
        fields.put("status", b.tilStatus);
        fields.put("reminderChannel", b.tilReminderChannel);
        fields.put("notes", b.tilNotes);

        beneficiary = new FormUtils.Dropdown(b.acBeneficiaryId);
        chw = new FormUtils.Dropdown(b.acChwId);
        status = new FormUtils.Dropdown(b.acStatus);
        reminder = new FormUtils.Dropdown(b.acReminderChannel);
        status.setOptions(FormUtils.options(Constants.APPOINTMENT_STATUSES));
        List<Option> channels = new ArrayList<>();
        channels.add(new Option("none", "No reminder"));
        channels.add(new Option("sms", "SMS"));
        channels.add(new Option("email", "Email"));
        reminder.setOptions(channels);
        FormUtils.bindDatePicker(this, b.etDate);
        bindTimePicker();

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
            b.toolbar.setTitle("Book appointment");
            if (savedInstanceState == null) {
                status.select("booked");
                reminder.select("sms");
            }
        } else {
            b.toolbar.setTitle("Edit appointment");
            vm.loadOne(id);
        }

        vm.getSingle().observe(this, state -> {
            if (state.status == Resource.Status.SUCCESS && state.data != null) {
                editing = state.data;
                originalDateTime = editing.datetime;
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
                Toast.makeText(this, "Appointment saved.", Toast.LENGTH_SHORT).show();
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

    private void bindTimePicker() {
        b.etTime.setFocusable(false);
        b.etTime.setClickable(true);
        b.etTime.setOnClickListener(v -> {
            String cur = FormUtils.text(b.etTime);
            int h = 9, m = 0;
            if (cur.matches("\\d{2}:\\d{2}")) { h = Integer.parseInt(cur.substring(0, 2)); m = Integer.parseInt(cur.substring(3)); }
            new TimePickerDialog(this, (picker, hh, mm) -> b.etTime.setText(String.format(Locale.US, "%02d:%02d", hh, mm)), h, m, true).show();
        });
    }

    private void populate(Appointment x) {
        beneficiary.select(x.beneficiaryId);
        chw.select(x.chwId);
        b.etPurpose.setText(x.purpose);
        if (DateUtils.isValidDateTime(x.datetime)) {
            b.etDate.setText(x.datetime.substring(0, 10));
            b.etTime.setText(x.datetime.substring(11));
        }
        status.select(x.status);
        reminder.select(x.reminderChannel == null ? "none" : x.reminderChannel);
        b.etNotes.setText(x.notes);
    }

    private void save() {
        Appointment x = editing != null ? editing : new Appointment();
        x.beneficiaryId = beneficiary.value();
        x.chwId = user.isAdmin() ? chw.value() : user.id;
        x.purpose = FormUtils.text(b.etPurpose);
        String date = FormUtils.text(b.etDate), time = FormUtils.text(b.etTime);
        x.datetime = date.isEmpty() || time.isEmpty() ? "" : date + "T" + time;
        x.status = status.value();
        x.reminderChannel = reminder.value();
        x.notes = FormUtils.text(b.etNotes);
        boolean dateChanged = editing == null || originalDateTime == null || !originalDateTime.equals(x.datetime);
        // Changing the time of a booked appointment automatically marks it as rescheduled (same rule as the web app)
        if (editing != null && dateChanged && "booked".equals(x.status)) x.status = "rescheduled";
        vm.save(x, beneficiary.label(), dateChanged);
    }
}
