package com.match.choms.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.match.choms.model.User;
import com.match.choms.repository.AuthRepository;
import com.match.choms.repository.RepoCallback;
import com.match.choms.repository.UserRepository;
import com.match.choms.util.Constants;
import com.match.choms.util.Resource;
import com.match.choms.util.Validators;

import java.util.List;

/** Administrator: list all accounts, approve pending ones (-> chw / coordinator / admin), edit name / phone / role, delete. */
public class ApprovalsViewModel extends ViewModel {
    private final UserRepository repo = new UserRepository();
    private final MutableLiveData<Resource<List<User>>> users = new MutableLiveData<>();
    private final MutableLiveData<Resource<String>> action = new MutableLiveData<>();

    public LiveData<Resource<List<User>>> getUsers() { return users; }
    public LiveData<Resource<String>> getAction() { return action; }
    public User currentUser() { return AuthRepository.getInstance().getCurrentUser(); }

    private User admin() {
        User u = currentUser();
        return u != null && u.isAdmin() ? u : null;
    }

    public void load() {
        if (admin() == null) { users.setValue(Resource.error("Only administrators can manage users.")); return; }
        users.setValue(Resource.loading());
        repo.listAll(new RepoCallback<List<User>>() {
            @Override public void onSuccess(List<User> r) { users.setValue(Resource.success(r)); }
            @Override public void onError(String m) { users.setValue(Resource.error(m)); }
        });
    }

    public void approve(User target, String role) {
        User admin = admin();
        if (admin == null) return;
        action.setValue(Resource.loading());
        repo.approve(target, role, admin, done(target.displayName() + " can now sign in as " + User.roleLabel(role) + "."));
    }

    /** Returns an error message for the form, or null when the save was started. */
    public String update(User target, String name, String phone, String role) {
        User admin = admin();
        if (admin == null) return "Only administrators can edit users.";
        String cleanName = Validators.clean(name, 80);
        if (cleanName.length() < 3) return "Enter the full name.";
        String cleanPhone = "";
        if (phone != null && !phone.trim().isEmpty()) {
            cleanPhone = Validators.normalisePhone(phone);
            if (cleanPhone == null) return "Enter a valid South African number, e.g. 082 123 4567.";
        }
        if (!Constants.ROLES.contains(role)) return "Choose a role.";
        action.setValue(Resource.loading());
        repo.update(target, cleanName, cleanPhone, role, admin, done("User updated."));
        return null;
    }

    public void setActive(User target, boolean activate) {
        User admin = admin();
        if (admin == null) return;
        action.setValue(Resource.loading());
        repo.setActive(target, activate, admin, done(target.displayName() + (activate ? " reactivated." : " deactivated.")));
    }

    public void delete(User target) {
        User admin = admin();
        if (admin == null) return;
        action.setValue(Resource.loading());
        repo.delete(target, admin, done(target.displayName() + " was deleted."));
    }

    private RepoCallback<String> done(String okMessage) {
        return new RepoCallback<String>() {
            @Override public void onSuccess(String id) { action.setValue(Resource.success(okMessage)); load(); }
            @Override public void onError(String m) { action.setValue(Resource.error(m)); }
        };
    }
}
