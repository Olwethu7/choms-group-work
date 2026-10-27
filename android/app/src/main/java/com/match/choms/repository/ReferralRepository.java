package com.match.choms.repository;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.Query;
import com.match.choms.model.Referral;
import com.match.choms.model.User;
import com.match.choms.util.Constants;
import com.match.choms.util.ErrorMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** All Firestore access for referrals (referrals/{id}). */
public class ReferralRepository extends BaseRepository {

    Query queryFor(User user) {
        return scoped(Constants.COL_REFERRALS, "chwId", user);
    }

    public void list(User user, RepoCallback<List<Referral>> cb) {
        queryFor(user).get()
                .addOnSuccessListener(snap -> {
                    List<Referral> list = toList(snap, Referral.class);
                    Collections.sort(list, (a, b) -> String.valueOf(b.referralDate).compareTo(String.valueOf(a.referralDate)));
                    cb.onSuccess(list);
                })
                .addOnFailureListener(e -> cb.onError(ErrorMapper.friendly(e)));
    }

    public void get(String id, RepoCallback<Referral> cb) {
        db.collection(Constants.COL_REFERRALS).document(id).get()
                .addOnSuccessListener(doc -> {
                    Referral r = doc.exists() ? doc.toObject(Referral.class) : null;
                    if (r == null) cb.onError("That referral no longer exists.");
                    else cb.onSuccess(r);
                })
                .addOnFailureListener(e -> cb.onError(ErrorMapper.friendly(e)));
    }

    public void save(Referral r, User actor, String beneficiaryName, RepoCallback<String> cb) {
        boolean isNew = r.id == null;
        DocumentReference ref = isNew
                ? db.collection(Constants.COL_REFERRALS).document()
                : db.collection(Constants.COL_REFERRALS).document(r.id);
        Map<String, Object> data = r.toMap();
        stamp(data, actor, isNew);
        saveWithAudit(ref, data, isNew ? "create" : "update", Constants.COL_REFERRALS, beneficiaryName + " to " + r.facility + " (" + r.status + ")", actor)
                .addOnSuccessListener(x -> cb.onSuccess(ref.getId()))
                .addOnFailureListener(e -> cb.onError(ErrorMapper.friendly(e)));
    }
}
