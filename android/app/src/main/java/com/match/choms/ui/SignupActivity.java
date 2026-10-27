package com.match.choms.ui;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputLayout;
import com.match.choms.databinding.ActivitySignupBinding;
import com.match.choms.util.Option;
import com.match.choms.util.Resource;
import com.match.choms.viewmodel.AuthViewModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Self sign-up. The account is created as "pending" and an administrator approves it. */
public class SignupActivity extends AppCompatActivity {
    private ActivitySignupBinding b;
    private AuthViewModel vm;
    private FormUtils.Dropdown role;
    private final Map<String, TextInputLayout> fields = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySignupBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        vm = new ViewModelProvider(this).get(AuthViewModel.class);
        b.toolbar.setNavigationOnClickListener(v -> finish());

        fields.put("name", b.tilName);
        fields.put("email", b.tilEmail);
        fields.put("phone", b.tilPhone);
        fields.put("role", b.tilRole);
        fields.put("password", b.tilPassword);
        fields.put("confirm", b.tilConfirm);

        // A new account starts as "pending"; the person says what they are and an administrator approves it.
        role = new FormUtils.Dropdown(b.acRole);
        List<Option> roles = new ArrayList<>();
        roles.add(new Option("chw", "Community Health Worker (CHW)"));
        roles.add(new Option("coordinator", "Programme Coordinator"));
        role.setOptions(roles);
        if (savedInstanceState == null) role.select("chw");


        vm.getSignupErrors().observe(this, errors -> {
            FormUtils.showErrors(errors, fields);
            String c = errors == null ? null : errors.get("consent");
            b.tvConsentError.setText(c);
            b.tvConsentError.setVisibility(c == null ? View.GONE : View.VISIBLE);
        });

        vm.getSignupState().observe(this, state -> {
            boolean loading = state.status == Resource.Status.LOADING;
            b.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
            b.btnSignup.setEnabled(!loading);
            b.btnSignup.setText(loading ? "Creating account..." : "Create account");
            if (state.status == Resource.Status.ERROR) {
                b.tvError.setText(state.message);
                b.tvError.setVisibility(View.VISIBLE);
            } else {
                b.tvError.setVisibility(View.GONE);
            }
            if (state.status == Resource.Status.SUCCESS) {
                b.formScroll.setVisibility(View.GONE);
                b.doneGroup.setVisibility(View.VISIBLE);
            }
        });

        b.btnSignup.setOnClickListener(v -> submit());
        b.etConfirm.setOnEditorActionListener((tv, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { submit(); return true; }
            return false;
        });
        b.btnBackToLogin.setOnClickListener(v -> finish());
    }

    private void submit() {
        b.tvError.setVisibility(View.GONE);
        vm.signup(FormUtils.text(b.etName), FormUtils.text(b.etEmail), FormUtils.text(b.etPhone), role.value(),
                FormUtils.text(b.etPassword), FormUtils.text(b.etConfirm), b.cbConsent.isChecked());
    }
}
