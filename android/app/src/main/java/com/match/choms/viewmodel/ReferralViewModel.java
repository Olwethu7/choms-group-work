package com.match.choms.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.match.choms.model.Beneficiary;
import com.match.choms.model.Referral;
import com.match.choms.model.User;
import com.match.choms.repository.AuthRepository;
import com.match.choms.repository.BeneficiaryRepository;
import com.match.choms.repository.ReferralRepository;
import com.match.choms.repository.RepoCallback;
import com.match.choms.repository.UserRepository;
import com.match.choms.util.Resource;
import com.match.choms.util.Validators;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Logic for the referrals list and the "new referral" form. */
public class ReferralViewModel extends ViewModel {
    private final ReferralRepository repo = new ReferralRepository();
    private final BeneficiaryRepository beneficiaryRepo = new BeneficiaryRepository();
    private final UserRepository userRepo = new UserRepository();

    private final MutableLiveData<Resource<List<Referral>>> list = new MutableLiveData<>();
    private final MutableLiveData<List<Beneficiary>> beneficiaries = new MutableLiveData<>();
    private final MutableLiveData<List<User>> chws = new MutableLiveData<>();
    private final MutableLiveData<Resource<Referral>> single = new MutableLiveData<>();
    private final MutableLiveData<Resource<String>> saveState = new MutableLiveData<>();
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>();

    public LiveData<Resource<List<Referral>>> getList() { return list; }
    public LiveData<List<Beneficiary>> getBeneficiaries() { return beneficiaries; }
    public LiveData<List<User>> getChws() { return chws; }
    public LiveData<Resource<Referral>> getSingle() { return single; }
    public LiveData<Resource<String>> getSaveState() { return saveState; }
    public LiveData<Map<String, String>> getFieldErrors() { return fieldErrors; }
    public User currentUser() { return AuthRepository.getInstance().getCurrentUser(); }

    public void load() {
        User u = currentUser();
        if (u == null) { list.setValue(Resource.error("Please sign in again.")); return; }
        list.setValue(Resource.loading());
        repo.list(u, new RepoCallback<List<Referral>>() {
            @Override public void onSuccess(List<Referral> result) { list.setValue(Resource.success(result)); }
            @Override public void onError(String message) { list.setValue(Resource.error(message)); }
        });
        loadBeneficiaries();
    }

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
        repo.get(id, new RepoCallback<Referral>() {
            @Override public void onSuccess(Referral r) { single.setValue(Resource.success(r)); }
            @Override public void onError(String message) { single.setValue(Resource.error(message)); }
        });
    }

    public void save(Referral r, String beneficiaryName) {
        User u = currentUser();
        if (u == null) { saveState.setValue(Resource.error("Please sign in again.")); return; }
        if (!u.isAdmin()) r.chwId = u.id;

        Map<String, String> errors = Validators.validateReferral(r);
        fieldErrors.setValue(errors);
        if (!errors.isEmpty()) {
            saveState.setValue(Resource.error("Please fix the highlighted fields."));
            return;
        }
        saveState.setValue(Resource.loading());
        repo.save(r, u, beneficiaryName, new RepoCallback<String>() {
            @Override public void onSuccess(String id) { saveState.setValue(Resource.success(id)); }
            @Override public void onError(String message) { saveState.setValue(Resource.error(message)); }
        });
    }
}
