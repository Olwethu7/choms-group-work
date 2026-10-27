package com.match.choms.util;

import androidx.annotation.Nullable;

/** Wraps a result so the UI can show loading / success / error states from one LiveData. */
public class Resource<T> {
    public enum Status { LOADING, SUCCESS, ERROR }

    public final Status status;
    @Nullable public final T data;
    @Nullable public final String message;

    private Resource(Status status, @Nullable T data, @Nullable String message) {
        this.status = status;
        this.data = data;
        this.message = message;
    }

    public static <T> Resource<T> loading() { return new Resource<>(Status.LOADING, null, null); }
    public static <T> Resource<T> success(@Nullable T data) { return new Resource<>(Status.SUCCESS, data, null); }
    public static <T> Resource<T> error(String message) { return new Resource<>(Status.ERROR, null, message); }
}
