package com.anokix.trader.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.anokix.trader.network.dto.LoginData;
import com.google.gson.Gson;

/**
 * Stores the auth token, user profile, portal type, and trader portal info in
 * SharedPreferences so session data survives across screens and app restarts.
 */
public class SessionManager {

    private static final String PREFS = "ax_session";
    private static final String KEY_TOKEN = "auth_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_FIRST_NAME = "first_name";
    private static final String KEY_LAST_NAME = "last_name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PHONE = "phone_number";
    private static final String KEY_ROLE_TYPE = "role_type";
    private static final String KEY_ROLE_LABEL = "role_label";
    private static final String KEY_PORTAL_TYPE = "portal_type";
    private static final String KEY_PORTAL_INFO_JSON = "portal_info_json";
    private static final String KEY_FCM_TOKEN = "fcm_token";
    private static final String KEY_FCM_TOKEN_REGISTERED = "fcm_token_registered";

    private static SessionManager instance;

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    private SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized SessionManager get(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
        return instance;
    }

    /** Persists everything needed from a successful login response. */
    public void saveLogin(LoginData data) {
        if (data == null) return;
        SharedPreferences.Editor ed = prefs.edit();
        if (data.token != null) {
            ed.putString(KEY_TOKEN, data.token);
        }
        if (data.portal_type != null) {
            ed.putString(KEY_PORTAL_TYPE, data.portal_type);
        }
        if (data.user != null) {
            LoginData.User u = data.user;
            ed.putString(KEY_USER_ID, safe(u.id));
            ed.putString(KEY_FIRST_NAME, safe(u.first_name));
            ed.putString(KEY_LAST_NAME, safe(u.last_name));
            ed.putString(KEY_EMAIL, safe(u.email));
            ed.putString(KEY_PHONE, safe(u.phone_number));
            ed.putString(KEY_ROLE_TYPE, safe(u.role_type));
            ed.putString(KEY_ROLE_LABEL, safe(u.role_label));
        }
        if (data.portal_info != null) {
            ed.putString(KEY_PORTAL_INFO_JSON, gson.toJson(data.portal_info));
        } else {
            ed.remove(KEY_PORTAL_INFO_JSON);
        }
        ed.apply();
    }

    public void setToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, "");
    }

    public boolean isLoggedIn() {
        return !getToken().isEmpty();
    }

    /** @deprecated Prefer {@link #saveLogin(LoginData)} after login. */
    @Deprecated
    public void setUser(String firstName, String lastName, String email, String roleLabel) {
        prefs.edit()
                .putString(KEY_FIRST_NAME, firstName)
                .putString(KEY_LAST_NAME, lastName)
                .putString(KEY_EMAIL, email)
                .putString(KEY_ROLE_LABEL, roleLabel)
                .apply();
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, "");
    }

    public String getFirstName() {
        return prefs.getString(KEY_FIRST_NAME, "");
    }

    public String getLastName() {
        return prefs.getString(KEY_LAST_NAME, "");
    }

    public String getFullName() {
        String name = (getFirstName() + " " + getLastName()).trim();
        return name.isEmpty() ? "" : name;
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, "");
    }

    public String getPhoneNumber() {
        return prefs.getString(KEY_PHONE, "");
    }

    public String getRoleType() {
        return prefs.getString(KEY_ROLE_TYPE, "");
    }

    public String getRoleLabel() {
        return prefs.getString(KEY_ROLE_LABEL, "");
    }

    public String getPortalType() {
        return prefs.getString(KEY_PORTAL_TYPE, "");
    }

    public LoginData.PortalInfo getPortalInfo() {
        String json = prefs.getString(KEY_PORTAL_INFO_JSON, null);
        Log.e("portal_info", json);
        if (json == null || json.isEmpty()) return null;
        try {
            return gson.fromJson(json, LoginData.PortalInfo.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Trading name or business name from saved trader portal info. */
    public String getStoreDisplayName() {
        LoginData.PortalInfo info = getPortalInfo();
        if (info != null) {
            String name = info.displayStoreName();
            if (!name.isEmpty()) return name;
        }
        return "";
    }

    // ---- FCM push token --------------------------------------------------

    /**
     * Caches the latest FCM token plus whether it has been registered with the
     * backend, so we only re-POST when the token actually changes.
     */
    public void saveFcmToken(String token, boolean registered) {
        prefs.edit()
                .putString(KEY_FCM_TOKEN, token == null ? "" : token)
                .putBoolean(KEY_FCM_TOKEN_REGISTERED, registered)
                .apply();
    }

    public String getFcmToken() {
        return prefs.getString(KEY_FCM_TOKEN, "");
    }

    /** True only when the cached token has already been accepted by the backend. */
    public boolean isFcmTokenRegistered(String token) {
        if (token == null || token.isEmpty()) return false;
        return token.equals(getFcmToken()) && prefs.getBoolean(KEY_FCM_TOKEN_REGISTERED, false);
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
