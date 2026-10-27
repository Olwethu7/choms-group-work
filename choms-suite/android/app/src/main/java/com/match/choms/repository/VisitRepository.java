package com.match.choms.repository;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.Query;
import com.match.choms.model.User;
import com.match.choms.model.Visit;
import com.match.choms.util.Constants;
import com.match.choms.util.ErrorMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** All Firestore access for visits (visits/{id}). */
public class VisitRepository extends BaseRepository {

    Query queryFor(User user) {
        return scoped(Constants.COL_VISITS, "chwId", user);
    }

    public void list(User user, RepoCallback<List<Visit>> cb) {
        queryFor(user).get()
                .addOnSuccessListener(snap -> {
                    List<Visit> list = toList(snap, Visit.class);
                    // newest first (done here so no Firestore composite index is needed)
                    Collections.sort(list, (a, b) -> String.valueOf(b.date).compareTo(String.valueOf(a.date)));
                    cb.onSuccess(list);
                })
                .addOnFailureListener(e -> cb.onError(ErrorMapper.friendly(e)));
    }

    public void get(String id, RepoCallback<Visit> cb) {
        db.collection(Constants.COL_VISITS).document(id).get()
                .addOnSuccessListener(doc -> {
                    Visit v = doc.exists() ? doc.toObject(Visit.class) : null;
                    if (v == null) cb.onError("That visit no longer exists.");
                    else cb.onSuccess(v);
                })
                .addOnFailureListener(e -> cb.onError(ErrorMapper.friendly(e)));
    }

    public void save(Visit v, User actor, String beneficiaryName, RepoCallback<String> cb) {
        boolean isNew = v.id == null;
        DocumentReference ref = isNew
                ? db.collection(Constants.COL_VISITS).document()
                : db.collection(Constants.COL_VISITS).document(v.id);
        Map<String, Object> data = v.toMap();
        stamp(data, actor, isNew);
        saveWithAudit(ref, data, isNew ? "create" : "update", Constants.COL_VISITS, beneficiaryName + " - " + v.status, actor)
                .addOnSuccessListener(x -> cb.onSuccess(ref.getId()))
                .addOnFailureListener(e -> cb.onError(ErrorMapper.friendly(e)));
    }
}
