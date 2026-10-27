package com.match.choms.repository;

import com.match.choms.model.Beneficiary;
import com.match.choms.model.User;
import com.match.choms.util.Constants;
import com.match.choms.util.ErrorMapper;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.Query;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** All Firestore access for beneficiaries (beneficiaries/{id}). */
public class BeneficiaryRepository extends BaseRepository {

    /** Query used by the repositories and the dashboard. */
    Query queryFor(User user) {
        return scoped(Constants.COL_BENEFICIARIES, "assignedChwId", user);
    }

    public void list(User user, RepoCallback<List<Beneficiary>> cb) {
        queryFor(user).get()
                .addOnSuccessListener(snap -> {
                    List<Beneficiary> list = toList(snap, Beneficiary.class);
                    Collections.sort(list, (a, b) -> (safe(a.lastName) + safe(a.firstName))
                            .compareToIgnoreCase(safe(b.lastName) + safe(b.firstName)));
                    cb.onSuccess(list);
                })
                .addOnFailureListener(e -> cb.onError(ErrorMapper.friendly(e)));
    }

    public void get(String id, RepoCallback<Beneficiary> cb) {
        db.collection(Constants.COL_BENEFICIARIES).document(id).get()
                .addOnSuccessListener(doc -> {
                    Beneficiary b = doc.exists() ? doc.toObject(Beneficiary.class) : null;
                    if (b == null) cb.onError("That beneficiary no longer exists.");
                    else cb.onSuccess(b);
                })
                .addOnFailureListener(e -> cb.onError(ErrorMapper.friendly(e)));
    }

    /** Create (b.id == null) or update a beneficiary. Returns the document id. */
    public void save(Beneficiary b, User actor, RepoCallback<String> cb) {
        boolean isNew = b.id == null;
        DocumentReference ref = isNew
                ? db.collection(Constants.COL_BENEFICIARIES).document()
                : db.collection(Constants.COL_BENEFICIARIES).document(b.id);

        Map<String, Object> data = b.toMap();
        if (isNew) data.put("ref", generateRef(ref.getId()));
        stamp(data, actor, isNew);

        // merge = true keeps any extra fields the web app may have written (e.g. ref)
        saveWithAudit(ref, data, isNew ? "create" : "update", Constants.COL_BENEFICIARIES, b.fullName(), actor)
                .addOnSuccessListener(v -> cb.onSuccess(ref.getId()))
                .addOnFailureListener(e -> cb.onError(ErrorMapper.friendly(e)));
    }

    /** Only Admins may delete beneficiaries (enforced by the Firestore rules too). */
    public void delete(Beneficiary b, User actor, RepoCallback<Void> cb) {
        if (!actor.isAdmin()) { cb.onError("Only an Administrator can delete beneficiaries."); return; }
        deleteWithAudit(db.collection(Constants.COL_BENEFICIARIES).document(b.id), Constants.COL_BENEFICIARIES, b.fullName(), actor)
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(e -> cb.onError(ErrorMapper.friendly(e)));
    }

    /** Same format as the web app: MCH-2026-AB12C */
    static String generateRef(String id) {
        String part = id.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        part = part.length() > 5 ? part.substring(0, 5) : String.format(Locale.ROOT, "%-5s", part).replace(' ', '0');
        return "MCH-" + Calendar.getInstance().get(Calendar.YEAR) + "-" + part;
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
