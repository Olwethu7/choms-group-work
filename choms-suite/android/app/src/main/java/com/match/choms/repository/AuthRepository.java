package com.match.choms.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.match.choms.util.DateUtils;
import com.match.choms.util.Validators;
import com.match.choms.model.User;
import com.match.choms.util.Constants;
import com.match.choms.util.ErrorMapper;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication + profile loading.
 * Step 1: Firebase Auth checks the email/password.
 * Step 2: the role is read from Firestore users/{uid} (same document the web app uses).
 * The signed-in profile is kept in memory so every screen can apply role-based access control.
 */
public class AuthRepository {
    private static AuthRepository instance;

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private User currentUser;

    private AuthRepository() { }

    public static synchronized AuthRepository getInstance() {
        if (instance == null) instance = new AuthRepository();
        return instance;
    }

    /** The profile of the signed-in user, or null when signed out. */
    public User getCurrentUser() { return currentUser; }

    public boolean hasFirebaseSession() { return auth.getCurrentUser() != null; }

    public void login(String email, String password, RepoCallback<User> cb) {
        auth.signInWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener(result -> {
                    FirebaseUser fu = result.getUser();
                    if (fu == null) { cb.onError("Sign-in failed. Please try again."); return; }
                    loadProfile(fu.getUid(), fu.getEmail(), cb);
                })
                .addOnFailureListener(e -> cb.onError(ErrorMapper.friendly(e)));
    }

    /** Called on app start: if Firebase still has a session, reload the profile instead of asking for a password. */
    public void restoreSession(RepoCallback<User> cb) {
        FirebaseUser fu = auth.getCurrentUser();
        if (fu == null) { cb.onError(""); return; }
        loadProfile(fu.getUid(), fu.getEmail(), cb);
    }

    private void loadProfile(String uid, String email, RepoCallback<User> cb) {
        db.collection(Constants.COL_USERS).document(uid).get()
                .addOnSuccessListener(doc -> {
                    User u = null;
                    try {
                        u = doc.exists() ? doc.toObject(User.class) : null;
                    } catch (RuntimeException ignored) { /* treated as missing profile below */ }
                    if (u != null && u.isPending()) {
                        logout();
                        cb.onError("Your account is waiting for an administrator to approve it. Please check back soon.");
                        return;
                    }
                    if (u != null && u.isDeactivated()) {
                        logout();
                        cb.onError("Your account has been deactivated. Please contact your administrator.");
                        return;
                    }
                    if (u == null || !u.canSignIn()) {
                        logout();
                        cb.onError("Your account has no CHOMS profile. Please contact your administrator.");
                        return;
                    }
                    u.id = uid;
                    if (u.email == null || u.email.isEmpty()) u.email = email;
                    currentUser = u;
                    cb.onSuccess(u);
                })
                .addOnFailureListener(e -> {
                    logout();
                    cb.onError(ErrorMapper.friendly(e));
                });
    }

    /**
     * Self sign-up. Creates the Firebase Auth account, then writes a LOCKED profile (role "pending", plus the
     * requestedRole chw / coordinator) together with its "register" audit entry in one batch.
     * Only an administrator can approve it and give the role.
     * If saving the profile fails, the Auth account is removed again so the person can simply retry.
     */
    public void register(String name, String email, String phone, String requestedRole, String password, RepoCallback<String> cb) {
        final String cleanEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        auth.createUserWithEmailAndPassword(cleanEmail, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser fu = result.getUser();
                    if (fu == null) { cb.onError("Sign-up failed. Please try again."); return; }
                    String normalised = phone == null || phone.trim().isEmpty() ? "" : Validators.normalisePhone(phone);
                    String cleanName = Validators.clean(name, 80);
                    DocumentReference log = db.collection(Constants.COL_AUDIT).document();
                    Map<String, Object> profile = new HashMap<>();
                    profile.put("name", cleanName);
                    profile.put("email", cleanEmail);
                    profile.put("phone", normalised == null ? "" : normalised);
                    profile.put("role", Constants.ROLE_PENDING);
                    profile.put("requestedRole", requestedRole);
                    profile.put("createdAt", DateUtils.nowISO());
                    profile.put("auditId", log.getId());
                    WriteBatch batch = db.batch();
                    batch.set(db.collection(Constants.COL_USERS).document(fu.getUid()), profile);
                    batch.set(log, BaseRepository.auditEntry("register", Constants.COL_USERS, fu.getUid(),
                            "Registered " + cleanName + " <" + cleanEmail + "> as " + requestedRole + " (pending)", fu.getUid(), cleanName, Constants.ROLE_PENDING));
                    batch.commit()
                            .addOnSuccessListener(x -> { auth.signOut(); cb.onSuccess(fu.getUid()); })
                            .addOnFailureListener(e -> {
                                fu.delete().addOnCompleteListener(t -> auth.signOut());
                                cb.onError(ErrorMapper.friendly(e));
                            });
                })
                .addOnFailureListener(e -> cb.onError(ErrorMapper.friendlySignup(e)));
    }

    public void sendPasswordReset(String email, RepoCallback<String> cb) {
        auth.sendPasswordResetEmail(email.trim())
                .addOnSuccessListener(x -> cb.onSuccess("sent"))
                .addOnFailureListener(e -> cb.onError(ErrorMapper.friendly(e)));
    }

    public void logout() {
        currentUser = null;
        auth.signOut();
    }
}
