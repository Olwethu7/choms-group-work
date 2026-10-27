package com.match.choms.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.match.choms.model.Beneficiary;
import com.match.choms.model.Appointment;
import com.match.choms.model.User;
import com.match.choms.repository.AuthRepository;
import com.match.choms.repository.BeneficiaryRepository;
import com.match.choms.repository.AppointmentRepository;
import com.match.choms.repository.RepoCallback;
import com.match.choms.repository.UserRepository;
import com.match.choms.util.Resource;
import com.match.choms.util.Validators;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Logic for the appointments list and the "new appointment" form. */
public class AppointmentViewModel extends ViewModel {
    private final AppointmentRepository repo = new AppointmentRepository();
    private final BeneficiaryRepository beneficiaryRepo = new BeneficiaryRepository();
    private final UserRepository userRepo = new UserRepository();

    private final MutableLiveData<Resource<List<Appointment>>> list = new MutableLiveData<>();
    private final MutableLiveData<List<Beneficiary>> beneficiaries = new MutableLiveData<>();
    private final MutableLiveData<List<User>> chws = new MutableLiveData<>();
    private final MutableLiveData<Resource<Appointment>> single = new MutableLiveData<>();
    private final MutableLiveData<Resource<String>> saveState = new MutableLiveData<>();
    private final MutableLiveData<Map<String, String>> fieldErrors = new MutableLiveData<>();

    public LiveData<Resource<List<Appointment>>> getList() { return list; }
    public LiveData<List<Beneficiary>> getBeneficiaries() { return beneficiaries; }
    public LiveData<List<User>> getChws() { return chws; }
    public LiveData<Resource<Appointment>> getSingle() { return single; }
    public LiveData<Resource<String>> getSaveState() { return saveState; }
    public LiveData<Map<String, String>> getFieldErrors() { return fieldErrors; }
    public User currentUser() { return AuthRepository.getInstance().getCurrentUser(); }

    public void load() {
        User u = currentUser();
        if (u == null) { list.setValue(Resource.error("Please sign in again.")); return; }
        list.setValue(Resource.loading());
        repo.list(u, new RepoCallback<List<Appointment>>() {
            @Override public void onSuccess(List<Appointment> result) { list.setValue(Resource.success(result)); }
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
        repo.get(id, new RepoCallback<Appointment>() {
            @Override public void onSuccess(Appointment r) { single.setValue(Resource.success(r)); }
            @Override public void onError(String message) { single.setValue(Resource.error(message)); }
        });
    }

    public void save(Appointment r, String beneficiaryName, boolean dateChanged) {
        User u = currentUser();
        if (u == null) { saveState.setValue(Resource.error("Please sign in again.")); return; }
        if (!u.isAdmin()) r.chwId = u.id;

        Map<String, String> errors = Validators.validateAppointment(r, dateChanged);
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
