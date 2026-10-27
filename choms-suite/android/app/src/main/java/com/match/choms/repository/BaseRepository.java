package com.match.choms.repository;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.DocumentSnapshot;
import com.match.choms.model.User;
import com.match.choms.util.Constants;
import com.match.choms.util.DateUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared Firestore helpers for all repositories (Repository pattern: the only layer that talks to Firebase). */
abstract class BaseRepository {
    private static final String TAG = "CHOMS";
    protected final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Role-scoped query. Admins and coordinators read the whole collection; a CHW only reads documents
     * whose owner field equals their UID. The Firestore rules enforce the same restriction on the
     * server, so a query WITHOUT this filter would be rejected for a CHW.
     */
    protected Query scoped(String collection, String ownerField, User user) {
        Query q = db.collection(collection);
        if (user == null || !user.isStaff()) {
            q = q.whereEqualTo(ownerField, user == null ? "" : user.id);
        }
        return q;
    }

    /** Convert documents to model objects, skipping any malformed document instead of crashing. */
    protected <T> List<T> toList(QuerySnapshot snap, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (DocumentSnapshot d : snap.getDocuments()) {
            try {
                T obj = d.toObject(type);
                if (obj != null) out.add(obj);
            } catch (RuntimeException ex) {
                Log.w(TAG, "Skipping unreadable document " + d.getId(), ex);
            }
        }
        return out;
    }

    /** Add audit fields (same names as the web app) to a map that is about to be saved. */
    protected void stamp(Map<String, Object> data, User actor, boolean isNew) {
        String now = DateUtils.nowISO();
        data.put("updatedAt", now);
        data.put("updatedBy", actor.id);
        if (isNew) {
            data.put("createdAt", now);
            data.put("createdBy", actor.id);
        }
    }

    /** The auditLogs document for one operation (same fields as the web app; required by the rules). */
    static Map<String, Object> auditEntry(String action, String collection, String docId, String summary,
                                          String userId, String userName, String role) {
        Map<String, Object> log = new HashMap<>();
        log.put("action", action);                 // register | approve | create | update | delete
        log.put("collection", collection);
        log.put("docId", docId);
        log.put("summary", cut(summary, 200));
        log.put("userId", userId);                 // must equal the signed-in UID (rules)
        log.put("userName", cut(userName, 80));
        log.put("role", role);                     // must equal the writer's current role (rules)
        log.put("at", DateUtils.nowISO());
        return log;
    }

    /**
     * Save (merge) a document AND its audit entry in ONE batch: either both are written or neither.
     * The document gets "auditId" so the Firestore rules can check the matching audit entry exists.
     */
    protected Task<Void> saveWithAudit(DocumentReference ref, Map<String, Object> data, String action,
                                       String collection, String summary, User actor) {
        DocumentReference log = db.collection(Constants.COL_AUDIT).document();
        data.put("auditId", log.getId());
        WriteBatch batch = db.batch();
        batch.set(ref, data, SetOptions.merge());
        batch.set(log, auditEntry(action, collection, ref.getId(), summary, actor.id, actor.displayName(), actor.role));
        return batch.commit();
    }

    /** Delete a document AND write its audit entry (fixed id "delete-<collection>-<id>", checked by the rules). */
    protected Task<Void> deleteWithAudit(DocumentReference ref, String collection, String summary, User actor) {
        DocumentReference log = db.collection(Constants.COL_AUDIT).document("delete-" + collection + "-" + ref.getId());
        WriteBatch batch = db.batch();
        batch.delete(ref);
        batch.set(log, auditEntry("delete", collection, ref.getId(), summary, actor.id, actor.displayName(), actor.role));
        return batch.commit();
    }

    private static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
