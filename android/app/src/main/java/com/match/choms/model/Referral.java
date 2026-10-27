package com.match.choms.model;

import com.google.firebase.firestore.DocumentId;

import java.util.HashMap;
import java.util.Map;

/** A referral of a beneficiary to a health facility. Firestore: referrals/{id}. */
public class Referral {
    @DocumentId public String id;
    public String beneficiaryId;
    public String chwId;
    public String facility;
    public String urgency = "routine";    // routine | urgent | emergency
    public String referralDate;           // yyyy-MM-dd
    public String status = "pending";     // pending | attended | completed | missed | cancelled
    public String reason;
    public String outcomeNotes;

    public Referral() { }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("beneficiaryId", beneficiaryId);
        m.put("chwId", chwId);
        m.put("facility", facility);
        m.put("urgency", urgency);
        m.put("referralDate", referralDate);
        m.put("status", status);
        m.put("reason", reason);
        m.put("outcomeNotes", outcomeNotes == null ? "" : outcomeNotes);
        return m;
    }
}
