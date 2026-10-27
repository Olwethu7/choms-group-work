package com.match.choms.ui;

import android.app.Activity;
import android.content.Intent;

import com.match.choms.model.User;
import com.match.choms.repository.AuthRepository;

/** If the app process was restarted there is no profile in memory: send the user back to the login screen. */
final class SessionGuard {
    private SessionGuard() { }

    /** Returns the signed-in user, or null after redirecting to the login screen. */
    static User require(Activity activity) {
        User u = AuthRepository.getInstance().getCurrentUser();
        if (u == null) {
            Intent i = new Intent(activity, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(i);
            activity.finish();
        }
        return u;
    }
}
