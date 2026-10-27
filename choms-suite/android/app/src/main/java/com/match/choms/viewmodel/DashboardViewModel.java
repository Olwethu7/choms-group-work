package com.match.choms.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.match.choms.model.DashboardStats;
import com.match.choms.model.User;
import com.match.choms.repository.AuthRepository;
import com.match.choms.repository.DashboardRepository;
import com.match.choms.repository.RepoCallback;
import com.match.choms.util.Resource;

/** Provides the dashboard / report statistics for the signed-in user's role. */
public class DashboardViewModel extends ViewModel {
    private final DashboardRepository repo = new DashboardRepository();
    private final MutableLiveData<Resource<DashboardStats>> stats = new MutableLiveData<>();

    public LiveData<Resource<DashboardStats>> getStats() { return stats; }
    public User currentUser() { return AuthRepository.getInstance().getCurrentUser(); }

    public void load() {
        User u = currentUser();
        if (u == null) { stats.setValue(Resource.error("Please sign in again.")); return; }
        stats.setValue(Resource.loading());
        repo.loadStats(u, new RepoCallback<DashboardStats>() {
            @Override public void onSuccess(DashboardStats result) { stats.setValue(Resource.success(result)); }
            @Override public void onError(String message) { stats.setValue(Resource.error(message)); }
        });
    }

    public void logout() { AuthRepository.getInstance().logout(); }
}
