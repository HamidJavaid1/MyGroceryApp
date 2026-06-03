package com.bazarlink.shared.api;

import retrofit2.Response;

/** User-facing messages from API failures (including HTML error pages). */
public final class ApiMessages {

    private ApiMessages() {
    }

    public static String fromResponse(Response<?> response, String fallback) {
        try {
            if (response.errorBody() != null) {
                return fromBody(response.errorBody().string(), fallback, response.code());
            }
        } catch (Exception ignored) {
        }
        return fallback + " (HTTP " + response.code() + ")";
    }

    public static String fromFailure(Throwable t, String fallback) {
        if (t == null) {
            return fallback;
        }
        String msg = t.getMessage();
        if (msg == null || msg.trim().isEmpty()) {
            return fallback;
        }
        if (looksLikeHtml(msg) || msg.contains("BEGIN_OBJECT") || msg.contains("JsonSyntax")) {
            return "Server returned an invalid response. Check that the backend is running at "
                    + ApiConfig.BASE_URL + " and you are signed in.";
        }
        return msg;
    }

    public static String fromBody(String body, String fallback, int httpCode) {
        if (body == null || body.trim().isEmpty()) {
            return fallback + " (HTTP " + httpCode + ")";
        }
        String trimmed = body.trim();
        if (looksLikeHtml(trimmed)) {
            return "Server error (HTTP " + httpCode + "). Is the API running at " + ApiConfig.BASE_URL + "?";
        }
        if (trimmed.contains("\"detail\"")) {
            return trimmed
                    .replace("{\"detail\":\"", "")
                    .replace("\"detail\": \"", "")
                    .replace("\"}", "")
                    .replace("\"", "")
                    .replace("[", "")
                    .replace("]", "");
        }
        return trimmed.length() > 160 ? trimmed.substring(0, 160) + "…" : trimmed;
    }

    private static boolean looksLikeHtml(String text) {
        String lower = text.toLowerCase();
        return lower.contains("<!doctype") || lower.contains("<html") || lower.contains("</html>");
    }
}
