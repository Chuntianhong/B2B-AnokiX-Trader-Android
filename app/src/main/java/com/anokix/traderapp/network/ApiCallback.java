package com.anokix.traderapp.network;

/**
 * Result callback delivered on the main thread.
 */
public interface ApiCallback<T> {
    void onSuccess(T data);

    /**
     * Same as {@link #onSuccess(Object)} but also carries the envelope's {@code message}.
     * Override when the server's wording is worth showing (e.g. "Till Sales ready — 2 rows.");
     * the default ignores it, so existing callbacks are unaffected.
     */
    default void onSuccess(T data, String message) {
        onSuccess(data);
    }

    void onError(String message);
}
