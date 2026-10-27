package com.match.choms.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.match.choms.databinding.ActivityListBinding;
import com.match.choms.model.User;
import com.match.choms.util.Constants;
import com.match.choms.util.Resource;
import com.match.choms.viewmodel.ApprovalsViewModel;

import java.util.ArrayList;
import java.util.List;

/** Administrator only: users screen. Approve pending sign-ups, edit name / phone / role, delete accounts. */
public class ApprovalsActivity extends AppCompatActivity {
    private ActivityListBinding binding;
    private ApprovalsViewModel vm;
    private RowAdapter adapter;
    private List<User> users = new ArrayList<>();
    private User me;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        me = SessionGuard.require(this);
        if (me == null) return;
        if (!me.isAdmin()) {
            Toast.makeText(this, "Only administrators can manage users.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        binding = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = new ViewModelProvider(this).get(ApprovalsViewModel.class);

        binding.toolbar.setTitle("Users");
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.etSearch.setHint("Search name, email or role...");
        binding.tvEmpty.setText("No accounts yet.");
        binding.fab.setVisibility(View.GONE);

        adapter = new RowAdapter(row -> {
            for (User u : users) if (u.id.equals(row.id)) { choose(u); break; }
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);
        binding.swipe.setOnRefreshListener(() -> vm.load());
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { adapter.filter(s.toString()); updateEmpty(); }
        });

        vm.getUsers().observe(this, state -> {
            binding.swipe.setRefreshing(state.status == Resource.Status.LOADING);
            if (state.status == Resource.Status.ERROR) {
                binding.tvError.setText(state.message);
                binding.tvError.setVisibility(View.VISIBLE);
            } else if (state.status == Resource.Status.SUCCESS && state.data != null) {
                binding.tvError.setVisibility(View.GONE);
                users = state.data;
                render();
            }
        });
        vm.getAction().observe(this, state -> {
            if (state.status == Resource.Status.SUCCESS || state.status == Resource.Status.ERROR) {
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (vm != null) vm.load();
    }

    private void render() {
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (User u : users) {
            RowAdapter.Row r = new RowAdapter.Row();
            r.id = u.id;
            r.title = u.displayName() + (u.id.equals(me.id) ? " (you)" : "");
            r.subtitle = u.email == null ? "" : u.email;
            r.detail = User.roleLabel(u.role) + "  |  " + u.statusLabel()
                    + (u.isPending() && u.requestedRole != null ? "  |  asked to be " + User.roleLabel(u.requestedRole) : "")
                    + (u.phone == null || u.phone.isEmpty() ? "" : "  |  " + u.phone);
            r.badge = u.isPending() ? "pending" : u.isDeactivated() ? "deactivated" : u.role;
            r.searchText = r.title + " " + r.subtitle + " " + r.detail;
            rows.add(r);
        }
        adapter.setRows(rows, String.valueOf(binding.etSearch.getText()));
        updateEmpty();
    }

    private void updateEmpty() {
        binding.tvEmpty.setVisibility(adapter.shownCount() == 0 && binding.tvError.getVisibility() != View.VISIBLE ? View.VISIBLE : View.GONE);
    }

    /** Actions for one account. */
    private void choose(User u) {
        boolean self = u.id.equals(me.id);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (u.isPending()) { labels.add("Approve..."); actions.add(() -> pickApprovalRole(u)); }
        labels.add("Edit"); actions.add(() -> edit(u));
        if (!self && !u.isPending()) {
            boolean off = u.isDeactivated();
            labels.add(off ? "Reactivate" : "Deactivate");
            actions.add(() -> confirmToggle(u, off));
        }
        if (!self) { labels.add("Delete permanently"); actions.add(() -> confirmDelete(u)); }
        new AlertDialog.Builder(this)
                .setTitle(u.displayName())
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton("Close", null)
                .show();
    }

    /** Approve: the admin confirms the role the person asked for, or picks another one. */
    private void pickApprovalRole(User u) {
        final String[] values = Constants.ACTIVE_ROLES.toArray(new String[0]);
        final String[] labels = new String[values.length];
        final int[] sel = {0};
        for (int i = 0; i < values.length; i++) {
            labels[i] = User.roleLabel(values[i]);
            if (values[i].equals(u.requestedRole)) sel[0] = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Approve " + u.displayName() + " as...")
                .setSingleChoiceItems(labels, sel[0], (d, which) -> sel[0] = which)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Approve", (d, w) -> vm.approve(u, values[sel[0]]))
                .show();
    }

    /** Edit dialog: name, phone, role (your own role cannot be changed). */
    private void edit(User u) {
        boolean self = u.id.equals(me.id);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        EditText name = new EditText(this);
        name.setHint("Full name");
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setText(u.name);
        EditText phone = new EditText(this);
        phone.setHint("Cell number");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        phone.setText(u.phone);
        TextView roleLabel = new TextView(this);
        roleLabel.setPadding(0, pad / 2, 0, 0);
        box.addView(name);
        box.addView(phone);
        box.addView(roleLabel);

        final String[] values = Constants.ROLES.toArray(new String[0]);
        final String[] chosen = {u.role == null ? Constants.ROLE_PENDING : u.role};
        Runnable showRole = () -> roleLabel.setText("Role: " + User.roleLabel(chosen[0]) + (self ? " (you cannot change your own role)" : "  - tap to change"));
        showRole.run();
        if (!self) {
            roleLabel.setOnClickListener(v -> {
                String[] labels = new String[values.length];
                int sel = 0;
                for (int i = 0; i < values.length; i++) { labels[i] = User.roleLabel(values[i]); if (values[i].equals(chosen[0])) sel = i; }
                new AlertDialog.Builder(this)
                        .setTitle("Role")
                        .setSingleChoiceItems(labels, sel, (d, which) -> { chosen[0] = values[which]; showRole.run(); d.dismiss(); })
                        .show();
            });
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit " + u.displayName())
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String error = vm.update(u, name.getText().toString(), phone.getText().toString(), chosen[0]);
            if (error == null) dialog.dismiss();
            else Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        }));
        dialog.show();
    }

    private void confirmToggle(User u, boolean activate) {
        new AlertDialog.Builder(this)
                .setTitle((activate ? "Reactivate " : "Deactivate ") + u.displayName() + "?")
                .setMessage(activate ? "They will be able to sign in again." : "They will no longer be able to sign in or see any records. Their history is kept.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(activate ? "Reactivate" : "Deactivate", (d, w) -> vm.setActive(u, activate))
                .show();
    }

    private void confirmDelete(User u) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + u.displayName() + "?")
                .setMessage("Their login and profile are removed permanently. Records they captured are kept.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> vm.delete(u))
                .show();
    }
}
