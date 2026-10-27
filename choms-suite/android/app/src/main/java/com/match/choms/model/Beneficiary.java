package com.match.choms.model;

import com.google.firebase.firestore.DocumentId;

import java.util.HashMap;
import java.util.Map;

/** A community member registered in a MatCH programme. Firestore: beneficiaries/{id}. */
public class Beneficiary {
    @DocumentId public String id;
    public String ref;
    public String firstName;
    public String lastName;
    public String idNumber;
    public String dob;              // yyyy-MM-dd
    public String gender;           // female | male | other
    public String phone;
    public String address;
    public String area;
    public String programme;
    public String enrolmentDate;    // yyyy-MM-dd
    public String status = "active";
    public String assignedChwId;    // UID of the CHW that owns this client
    public boolean consent;         // POPIA consent - Firestore rules reject a save without it

    public Beneficiary() { }

    public String fullName() {
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }

    /** Fields written to Firestore (same field names as the web app). */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("firstName", firstName);
        m.put("lastName", lastName);
        m.put("idNumber", idNumber == null ? "" : idNumber);
        m.put("dob", dob);
        m.put("gender", gender);
        m.put("phone", phone == null ? "" : phone);
        m.put("address", address == null ? "" : address);
        m.put("area", area);
        m.put("programme", programme);
        m.put("enrolmentDate", enrolmentDate);
        m.put("status", status);
        m.put("assignedChwId", assignedChwId);
        m.put("consent", consent);
        return m;
    }
}
