package com.match.choms.model;

import com.google.firebase.firestore.DocumentId;

import java.util.HashMap;
import java.util.Map;

/** A home visit made (or scheduled) by a CHW. Firestore: visits/{id}. */
public class Visit {
    @DocumentId public String id;
    public String beneficiaryId;
    public String chwId;            // UID of the CHW - rules let a CHW touch only their own visits
    public String status = "completed";   // scheduled | completed | cancelled
    public String date;             // yyyy-MM-dd
    public String location;
    public String outcome;
    public String notes;

    public Visit() { }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("beneficiaryId", beneficiaryId);
        m.put("chwId", chwId);
        m.put("status", status);
        m.put("date", date);
        m.put("location", location == null ? "" : location);
        m.put("outcome", outcome == null ? "" : outcome);
        m.put("notes", notes == null ? "" : notes);
        return m;
    }
}
