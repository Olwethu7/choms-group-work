package com.match.choms.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.match.choms.model.Beneficiary;
import com.match.choms.model.User;
import com.match.choms.model.Visit;
import com.match.choms.repository.AuthRepository;
import com.match.choms.repository.BeneficiaryRepository;
import com.match.choms.repository.RepoCallback;
import com.match.choms.repository.UserRepository;
import com.match.choms.repository.VisitRepository;
import com.match.choms.util.Resource;
import com.match.choms.util.Validators;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Logic for the visits list and the "record visit" form. */
public class VisitViewModel extends ViewModel {
    private final VisitRepository repo = new VisitRepository();
    private final BeneficiaryRepository beneficiaryRepo = new BeneficiaryRepository();
    private final UserRepository userRepo = new UserRepository();

    private final MutableLiveData<Resource<List<Visit>>> list = new MutableLiveData<>();
    private final MutableLiveData<List<Beneficiary>> beneficiaries = new MutableLiveData<>();
    private final MutableLiveData<List<User>> chws = new MutableLiveData<>();
    private final MutableLiveData<Resource<Visit>> single = new MutableLiveData<>();
    private final MutableLiveData<Resource<String>> saveState = new MutableLiveData<>();
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>();

    public LiveData<Resource<List<Visit>>> getList() { return list; }
    public LiveData<List<Beneficiary>> getBeneficiaries() { return beneficiaries; }
    public LiveData<List<User>> getChws() { return chws; }
    public LiveData<Resource<Visit>> getSingle() { return single; }
    public LiveData<Resource<String>> getSaveState() { return saveState; }
    public LiveData<Map<String, String>> getFieldErrors() { return fieldErrors; }
    public User currentUser() { return AuthRepository.getInstance().getCurrentUser(); }

    public void load() {
        User u = currentUser();
        if (u == null) { list.setValue(Resource.error("Please sign in again.")); return; }
        list.setValue(Resource.loading());
        repo.list(u, new RepoCallback<List<Visit>>() {
            @Override public void onSuccess(List<Visit> result) { list.setValue(Resource.success(result)); }
            @Override public void onError(String message) { list.setValue(Resource.error(message)); }
        });
        loadBeneficiaries();
    }

    /** Beneficiaries are needed to show names and to fill the "Beneficiary" drop-down. */
    public void loadBeneficiaries() {
        User u = currentUser();
        if (u == null) return;
        beneficiaryRepo.list(u, new RepoCallback<List<Beneficiary>>() {
            @Override public void onSuccess(List<Beneficiary> result) { beneficiaries.setValue(result); }
            @Override public void onError(String message) { beneficiaries.setValue(new ArrayList<>()); }
        });
        if (u.isAdmin()) {
            userRepo.listChws(new RepoCallback<List<User>>() {
                @Override public void onSuccess(List<User> result) { chws.setValue(result); }
                @Override public void onError(String message) { chws.setValue(new ArrayList<>()); }
            });
        }
    }

    public void loadOne(String id) {
        single.setValue(Resource.loading());
        repo.get(id, new RepoCallback<Visit>() {
            @Override public void onSuccess(Visit v) { single.setValue(Resource.success(v)); }
            @Override public void onError(String message) { single.setValue(Resource.error(message)); }
        });
    }

    public void save(Visit v, String beneficiaryName) {
        User u = currentUser();
        if (u == null) { saveState.setValue(Resource.error("Please sign in again.")); return; }
        if (!u.isAdmin()) v.chwId = u.id;           // CHWs always record visits under their own UID

        Map<String, String> errors = Validators.validateVisit(v, v.id == null);
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
            saveState.setValue(Resource.error("Please fix the highlighted fields."));
            return;
        }
        saveState.setValue(Resource.loading());
        repo.save(v, u, beneficiaryName, new RepoCallback<String>() {
            @Override public void onSuccess(String id) { saveState.setValue(Resource.success(id)); }
            @Override public void onError(String message) { saveState.setValue(Resource.error(message)); }
        });
    }
}
