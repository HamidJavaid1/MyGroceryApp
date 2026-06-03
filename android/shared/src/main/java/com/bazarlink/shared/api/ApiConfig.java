package com.bazarlink.shared.api;

/**
 * Backend base URL for all Android modules.
 * <ul>
 *   <li>Real phone on same Wi‑Fi: your PC's LAN IP (current default below)</li>
 *   <li>Android emulator: use {@link #EMULATOR_BASE_URL}</li>
 * </ul>
 */
public final class ApiConfig {
    /** Live backend on Render */
    public static final String BASE_URL = "https://mygroceryapp-4ryn.onrender.com/";

    /** Emulator alias for the host machine's localhost. */
    public static final String EMULATOR_BASE_URL = "http://10.0.2.2:8000/";

    private ApiConfig() {
    }

    public static boolean isEmulatorUrl(String url) {
        return url != null && url.contains("10.0.2.2");
    }
}
