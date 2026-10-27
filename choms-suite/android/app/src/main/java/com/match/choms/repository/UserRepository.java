package com.match.choms.repository;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.functions.FirebaseFunctions;
import com.match.choms.model.User;
import com.match.choms.util.Constants;
import com.match.choms.util.DateUtils;
import com.match.choms.util.ErrorMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User accounts (Admin only, enforced by the Firestore rules).
 * Every change is written together with its auditLogs entry; deleting goes through the deleteUser Cloud Function,
 * because only the server can remove another person's Firebase Auth login.
 */
public class UserRepository extends BaseRepository {

    /** Approved Community Health Workers - used to assign clients, visits, referrals and appointments. */
    public void listChws(RepoCallback<List<User>> cb) {
        db.collection(Constants.COL_USERS).whereEqualTo("role", Constants.ROLE_CHW).get()
                .addOnSuccessListener(snap -> {
                    List<User> active = new ArrayList<>();
                    for (User u : toList(snap, User.class)) if (!u.isDeactivated()) active.add(u);   // deactivated CHWs get no new work
                    cb.onSuccess(sorted(active));
                })
                .addOnFailureListener(e -> cb.onError(ErrorMapper.friendly(e)));
    }

    /** Everyone: pending accounts first, then by name. */
    public void listAll(RepoCallback<List<User>> cb) {
        db.collection(Constants.COL_USERS).get()
                .addOnSuccessListener(snap -> {
                    List<User> all = sorted(toList(snap, User.class));
                    List<User> out = new ArrayList<>();
                    for (User u : all) if (u.isPending()) out.add(u);
                    for (User u : all) if (!u.isPending()) out.add(u);
                    cb.onSuccess(out);
                })
                .addOnFailureListener(e -> cb.onError(ErrorMapper.friendly(e)));
    }

    /** Approve: pending -> chw / coordinator / admin (the admin confirms or changes what the person asked for). */
    public void approve(User target, String role, User admin, RepoCallback<String> cb) {
        if (!Constants.ACTIVE_ROLES.contains(role)) { cb.onError("Choose a role."); return; }
        Map<String, Object> data = new HashMap<>();
        data.put("role", role);
        data.put("status", "active");
        data.put("approvedBy", admin.displayName());
        data.put("approvedAt", DateUtils.nowISO());
        write(target, data, "approve", "Approved " + target.displayName() + " as " + role, admin, cb);
    }

    /** Edit name, phone and role. Email cannot change. An admin cannot change their own role. */
    public void update(User target, String name, String phone, String role, User admin, RepoCallback<String> cb) {
        if (target.id.equals(admin.id) && !Constants.ROLE_ADMIN.equals(role)) {
            cb.onError("You cannot change your own role.");
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("phone", phone == null ? "" : phone);
        data.put("role", role);
        boolean approving = target.isPending() && !Constants.ROLE_PENDING.equals(role);
        if (approving) {
            data.put("approvedBy", admin.displayName());
            data.put("approvedAt", DateUtils.nowISO());
        }
        List<String> changes = new ArrayList<>();
        if (!name.equals(target.name)) changes.add("name");
        if (!(phone == null ? "" : phone).equals(target.phone == null ? "" : target.phone)) changes.add("phone");
        if (!role.equals(target.role)) changes.add("role " + target.role + " -> " + role);
        String summary = "Edited user " + name + ": " + (changes.isEmpty() ? "no changes" : String.join(", ", changes));
        write(target, data, approving ? "approve" : "update", summary, admin, cb);
    }

    /** Deactivate (cannot sign in, keeps history) or reactivate an account. */
    public void setActive(User target, boolean activate, User admin, RepoCallback<String> cb) {
        if (target.id.equals(admin.id)) { cb.onError("You cannot deactivate your own account."); return; }
        Map<String, Object> data = new HashMap<>();
        data.put("status", activate ? "active" : "deactivated");
        write(target, data, "update", (activate ? "Reactivated " : "Deactivated ") + target.displayName(), admin, cb);
    }

    /** Permanently deletes the login + profile. The Cloud Function checks the caller is an admin and writes the audit entry. */
    public void delete(User target, User admin, RepoCallback<String> cb) {
        if (target.id.equals(admin.id)) { cb.onError("You cannot delete your own account."); return; }
        Map<String, Object> args = new HashMap<>();
        args.put("uid", target.id);
        FirebaseFunctions.getInstance(Constants.FUNCTIONS_REGION).getHttpsCallable("deleteUser").call(args)
                .addOnSuccessListener(r -> cb.onSuccess(target.id))
                .addOnFailureListener(e -> {
                    if (!functionMissing(e)) { cb.onError(ErrorMapper.friendly(e)); return; }
                    // No Cloud Function (Spark plan): remove the profile + audit entry; the Auth login stays.
                    deleteWithAudit(db.collection(Constants.COL_USERS).document(target.id), Constants.COL_USERS,
                            "Deleted user " + target.displayName() + " <" + target.email + "> (" + target.role + "); profile only - Auth login kept", admin)
                            .addOnSuccessListener(x -> cb.onSuccess(target.id))
                            .addOnFailureListener(e2 -> cb.onError(ErrorMapper.friendly(e2)));
                });
    }

    private static boolean functionMissing(Exception e) {
        if (!(e instanceof com.google.firebase.functions.FirebaseFunctionsException)) return false;
        com.google.firebase.functions.FirebaseFunctionsException.Code c = ((com.google.firebase.functions.FirebaseFunctionsException) e).getCode();
        return c == com.google.firebase.functions.FirebaseFunctionsException.Code.NOT_FOUND
                || (c == com.google.firebase.functions.FirebaseFunctionsException.Code.INTERNAL && "INTERNAL".equalsIgnoreCase(e.getMessage()));
    }

    private void write(User target, Map<String, Object> data, String action, String summary, User admin, RepoCallback<String> cb) {
        stamp(data, admin, false);
        DocumentReference ref = db.collection(Constants.COL_USERS).document(target.id);
        saveWithAudit(ref, data, action, Constants.COL_USERS, summary, admin)
                .addOnSuccessListener(x -> cb.onSuccess(target.id))
                .addOnFailureListener(e -> cb.onError(ErrorMapper.friendly(e)));
    }

    private static List<User> sorted(List<User> list) {
        Collections.sort(list, (a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
        return list;
    }
}
