package com.match.choms.util;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.functions.FirebaseFunctionsException;

/** Turns technical Firebase exceptions into messages a Community Health Worker can understand. */
public final class ErrorMapper {
    private ErrorMapper() { }

    /** Sign-up specific messages (an existing email must be reported as such, unlike sign-in). */
    public static String friendlySignup(Exception e) {
        if (e instanceof FirebaseAuthUserCollisionException) return "An account with this email already exists. Try signing in instead.";
        if (e instanceof FirebaseAuthWeakPasswordException) return "That password is too weak. Use at least 8 characters with upper-case, lower-case and a number.";
        if (e instanceof FirebaseAuthInvalidCredentialsException) return "Enter a valid email address.";
        return friendly(e);
    }

    public static String friendly(Exception e) {
        if (e == null) return "Something went wrong. Please try again.";
        if (e instanceof FirebaseAuthInvalidCredentialsException || e instanceof FirebaseAuthInvalidUserException) {
            return "Incorrect email or password."; // same message for both: do not reveal which one is wrong
        }
        if (e instanceof FirebaseTooManyRequestsException) {
            return "Too many attempts. Please wait a few minutes and try again.";
        }
        if (e instanceof FirebaseNetworkException) {
            return "No internet connection. Please check your data or Wi-Fi and try again.";
        }
        if (e instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) e).getErrorCode();
            if ("ERROR_INVALID_CREDENTIAL".equals(code) || "ERROR_WRONG_PASSWORD".equals(code)
                    || "ERROR_USER_NOT_FOUND".equals(code)) return "Incorrect email or password.";
            if ("ERROR_USER_DISABLED".equals(code)) return "This account has been disabled.";
            if ("ERROR_INVALID_EMAIL".equals(code)) return "Enter a valid email address.";
        }
        if (e instanceof FirebaseFunctionsException) {
            FirebaseFunctionsException.Code code = ((FirebaseFunctionsException) e).getCode();
            // NOT_FOUND (or a bare INTERNAL) means the deleteUser Cloud Function is not deployed / not reachable
            if (code == FirebaseFunctionsException.Code.NOT_FOUND
                    || (code == FirebaseFunctionsException.Code.INTERNAL && "INTERNAL".equalsIgnoreCase(e.getMessage())))
                return "The user-deletion service is not available yet. Ask the administrator to deploy the Cloud Function.";
            if (code == FirebaseFunctionsException.Code.UNAVAILABLE) return "No internet connection. Please check your data or Wi-Fi and try again.";
            if (e.getMessage() != null && !e.getMessage().isEmpty()) return e.getMessage();   // our own friendly HttpsError text
        }
        if (e instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) e).getCode()) {
                case PERMISSION_DENIED: return "You do not have permission to do that.";
                case UNAVAILABLE: return "The server is unreachable. Please check your connection.";
                case UNAUTHENTICATED: return "Your session has expired - please sign in again.";
                case DEADLINE_EXCEEDED: return "The request took too long. Please try again.";
                case NOT_FOUND: return "That record no longer exists.";
                default: break;
            }
        }
        String msg = e.getMessage();
        if (msg != null && msg.toLowerCase().contains("network")) {
            return "No internet connection. Please check your data or Wi-Fi and try again.";
        }
        return "Something went wrong. Please try again.";
    }
}
