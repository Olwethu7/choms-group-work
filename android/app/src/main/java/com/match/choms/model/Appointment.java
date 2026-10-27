package com.match.choms.model;

import com.google.firebase.firestore.DocumentId;

import java.util.HashMap;
import java.util.Map;

/** A booked clinic / follow-up appointment. Firestore: appointments/{id} (same fields as the web app). */
public class Appointment {
    @DocumentId public String id;
    public String beneficiaryId;
    public String chwId;
    public String datetime;               // yyyy-MM-ddTHH:mm (local time, same as the web app)
    public String purpose;
    public String status = "booked";      // booked | rescheduled | attended | missed | cancelled
    public String reminderChannel = "sms";// none | sms | email
    public String reminderDue = "";
    public String notes;

    public Appointment() { }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("beneficiaryId", beneficiaryId);
        m.put("chwId", chwId);
        m.put("datetime", datetime);
        m.put("purpose", purpose);
        m.put("status", status);
        m.put("reminderChannel", reminderChannel);
        m.put("reminderDue", reminderDue == null ? "" : reminderDue);
        m.put("notes", notes == null ? "" : notes);
        return m;
    }
}
