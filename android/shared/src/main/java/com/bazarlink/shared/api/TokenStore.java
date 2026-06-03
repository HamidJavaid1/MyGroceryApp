package com.bazarlink.shared.api;

import android.content.Context;
import android.content.SharedPreferences;

public class TokenStore {
    private final SharedPreferences prefs;
    private static final String KEY_SERVER_URL = "server_url";

    public TokenStore(Context context) {
        prefs = context.getSharedPreferences("bazarlink_auth", Context.MODE_PRIVATE);
    }

    public String accessToken() {
        return prefs.getString("access", "");
    }

    public void save(String access, String refresh) {
        prefs.edit().putString("access", access).putString("refresh", refresh).apply();
    }

    public void clear() {
        prefs.edit().remove("access").remove("refresh").apply();
    }

    public String serverUrl() {
        return prefs.getString(KEY_SERVER_URL, "");
    }

    public void saveServerUrl(String serverUrl) {
        prefs.edit().putString(KEY_SERVER_URL, serverUrl).apply();
    }
}
