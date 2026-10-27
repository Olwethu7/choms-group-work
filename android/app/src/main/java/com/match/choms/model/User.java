package com.match.choms.model;

import com.google.firebase.firestore.DocumentId;
import com.match.choms.util.Constants;

/**
 * A person who can sign in. Stored in Firestore at users/{uid}.
 * role: pending (registered, waiting for approval) | chw | coordinator | admin.
 */
public class User {
    @DocumentId public String id;   // = Firebase Auth UID
    public String name;
    public String email;
    public String phone;
    public String role;             // pending | chw | coordinator | admin
    public String requestedRole;    // chw | coordinator - what the person asked for at sign-up
    public String status;           // active | deactivated (missing = active)
    public String fullName;         // older / hand-made profiles may use fullName instead of name
    public String createdAt;

    /** Required by Firestore for toObject(). */
    public User() { }

    public boolean isAdmin() { return Constants.ROLE_ADMIN.equals(role); }
    public boolean isCoordinator() { return Constants.ROLE_COORDINATOR.equals(role); }
    public boolean isChw() { return Constants.ROLE_CHW.equals(role); }
    public boolean isPending() { return Constants.ROLE_PENDING.equals(role); }
    /** Admins and coordinators see everyone's records; a CHW only their own. */
    public boolean isStaff() { return isAdmin() || isCoordinator(); }
    /** Admins and CHWs may add / change records. Coordinators only view and report. */
    public boolean canEditRecords() { return isAdmin() || isChw(); }
    public boolean isDeactivated() { return "deactivated".equals(status); }
    /** Approved accounts that are not deactivated may sign in. */
    public boolean canSignIn() { return (isAdmin() || isCoordinator() || isChw()) && !isDeactivated(); }
    /** active | pending | deactivated - shown in the Users screen. */
    public String statusLabel() { return isPending() ? "Pending approval" : isDeactivated() ? "Deactivated" : "Active"; }
    public String displayName() {
        if (name != null && !name.isEmpty()) return name;
        if (fullName != null && !fullName.isEmpty()) return fullName;
        return email == null ? "User" : email;
    }

    public static String roleLabel(String role) {
        if (Constants.ROLE_ADMIN.equals(role)) return "Administrator";
        if (Constants.ROLE_COORDINATOR.equals(role)) return "Programme Coordinator";
        if (Constants.ROLE_CHW.equals(role)) return "Community Health Worker";
        if (Constants.ROLE_PENDING.equals(role)) return "Pending approval";
        return role == null ? "-" : role;
    }
}
