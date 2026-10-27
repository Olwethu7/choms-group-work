package com.match.choms.repository;

/** Simple success/error callback used between Repository and ViewModel. */
public interface RepoCallback<T> {
    void onSuccess(T result);
    void onError(String message);
}
