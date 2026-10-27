package com.match.choms.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.match.choms.model.Beneficiary;
import com.match.choms.model.User;
import com.match.choms.repository.AuthRepository;
import com.match.choms.repository.BeneficiaryRepository;
import com.match.choms.repository.RepoCallback;
import com.match.choms.repository.UserRepository;
import com.match.choms.util.Resource;
import com.match.choms.util.Validators;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Logic for the beneficiary list and the add/edit form. */
public class BeneficiaryViewModel extends ViewModel {
    private final BeneficiaryRepository repo = new BeneficiaryRepository();
    private final UserRepository userRepo = new UserRepository();

    private final MutableLiveData<Resource<List<Beneficiary>>> list = new MutableLiveData<>();
    private final MutableLiveData<Resource<Beneficiary>> single = new MutableLiveData<>();
    private final MutableLiveData<Resource<String>> saveState = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> deleteState = new MutableLiveData<>();
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>();
    private final MutableLiveData<List<User>> chws = new MutableLiveData<>();

    private List<Beneficiary> loaded = new ArrayList<>();

    public LiveData<Resource<List<Beneficiary>>> getList() { return list; }
    public LiveData<Resource<Beneficiary>> getSingle() { return single; }
    public LiveData<Resource<String>> getSaveState() { return saveState; }
    public LiveData<Resource<Void>> getDeleteState() { return deleteState; }
    public LiveData<Map<String, String>> getFieldErrors() { return fieldErrors; }
    public LiveData<List<User>> getChws() { return chws; }
    public User currentUser() { return AuthRepository.getInstance().getCurrentUser(); }

    public void load() {
        User u = currentUser();
        if (u == null) { list.setValue(Resource.error("Please sign in again.")); return; }
        list.setValue(Resource.loading());
        repo.list(u, new RepoCallback<List<Beneficiary>>() {
            @Override public void onSuccess(List<Beneficiary> result) {
                loaded = result;
                list.setValue(Resource.success(result));
            }
            @Override public void onError(String message) { list.setValue(Resource.error(message)); }
        });
    }

    /** Only needed by Admin/Coordinator (to pick which CHW owns the client). */
    public void loadChws() {
        User u = currentUser();
        if (u == null || !u.isAdmin()) return;
        userRepo.listChws(new RepoCallback<List<User>>() {
            @Override public void onSuccess(List<User> result) { chws.setValue(result); }
            @Override public void onError(String message) { chws.setValue(new ArrayList<>()); }
        });
    }

    public void loadOne(String id) {
        single.setValue(Resource.loading());
        repo.get(id, new RepoCallback<Beneficiary>() {
            @Override public void onSuccess(Beneficiary b) { single.setValue(Resource.success(b)); }
            @Override public void onError(String message) { single.setValue(Resource.error(message)); }
        });
    }

    /** Validate first; only a valid form is sent to Firestore. */
    public void save(Beneficiary b) {
        User u = currentUser();
        if (u == null) { saveState.setValue(Resource.error("Please sign in again.")); return; }
        if (!u.isAdmin()) b.assignedChwId = u.id;   // a CHW can only register clients for themselves

        Map<String, String> errors = Validators.validateBeneficiary(b, loaded);
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
            saveState.setValue(Resource.error("Please fix the highlighted fields."));
            return;
        }
        saveState.setValue(Resource.loading());
        repo.save(b, u, new RepoCallback<String>() {
            @Override public void onSuccess(String id) { saveState.setValue(Resource.success(id)); }
            @Override public void onError(String message) { saveState.setValue(Resource.error(message)); }
        });
    }

    public void delete(Beneficiary b) {
        User u = currentUser();
        if (u == null) return;
        deleteState.setValue(Resource.loading());
        repo.delete(b, u, new RepoCallback<Void>() {
            @Override public void onSuccess(Void v) { deleteState.setValue(Resource.success(null)); }
            @Override public void onError(String message) { deleteState.setValue(Resource.error(message)); }
        });
    }
}
