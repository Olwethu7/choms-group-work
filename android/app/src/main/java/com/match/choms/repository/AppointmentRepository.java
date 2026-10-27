package com.match.choms.repository;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.Query;
import com.match.choms.model.Appointment;
import com.match.choms.model.User;
import com.match.choms.util.Constants;
import com.match.choms.util.ErrorMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** All Firestore access for appointments (appointments/{id}). */
public class AppointmentRepository extends BaseRepository {

    Query queryFor(User user) {
        return scoped(Constants.COL_APPOINTMENTS, "chwId", user);
    }

    public void list(User user, RepoCallback<List<Appointment>> cb) {
        queryFor(user).get()
                .addOnSuccessListener(snap -> {
                    List<Appointment> list = toList(snap, Appointment.class);
                    Collections.sort(list, (a, b) -> String.valueOf(b.datetime).compareTo(String.valueOf(a.datetime)));
                    cb.onSuccess(list);
                })
                .addOnFailureListener(e -> cb.onError(ErrorMapper.friendly(e)));
    }

    public void get(String id, RepoCallback<Appointment> cb) {
        db.collection(Constants.COL_APPOINTMENTS).document(id).get()
                .addOnSuccessListener(doc -> {
                    Appointment a = doc.exists() ? doc.toObject(Appointment.class) : null;
                    if (a == null) cb.onError("That appointment no longer exists.");
                    else cb.onSuccess(a);
                })
                .addOnFailureListener(e -> cb.onError(ErrorMapper.friendly(e)));
    }

    public void save(Appointment a, User actor, String beneficiaryName, RepoCallback<String> cb) {
        boolean isNew = a.id == null;
        DocumentReference ref = isNew
                ? db.collection(Constants.COL_APPOINTMENTS).document()
                : db.collection(Constants.COL_APPOINTMENTS).document(a.id);
        Map<String, Object> data = a.toMap();
        stamp(data, actor, isNew);
        saveWithAudit(ref, data, isNew ? "create" : "update", Constants.COL_APPOINTMENTS, beneficiaryName + " @ " + a.datetime + " (" + a.status + ")", actor)
                .addOnSuccessListener(x -> cb.onSuccess(ref.getId()))
                .addOnFailureListener(e -> cb.onError(ErrorMapper.friendly(e)));
    }
}
