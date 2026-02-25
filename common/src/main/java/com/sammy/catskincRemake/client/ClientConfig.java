package com.sammy.catskincRemake.client;

public final class ClientConfig {
    public int openUiKey = 75;
    public long refreshIntervalMs = 1_000L;
    public int ensureIntervalTicks = 20;
    public int ensureLimitPerPass = 16;

    public float uiScale = 1.0F;

    public String apiBaseUrl = "https://storage-api.catskin.space";
    public String pathUpload = "/upload";
    public String pathSelect = "/select";
    public String pathSelected = "/selected";
    public String pathPublic = "/public/";
    public String pathEvents = "/events";
    public int timeoutMs = 15_000;
    public boolean debugLogging = false;
    public boolean traceLogging = false;

    public void sanitize() {
        openUiKey = clamp(openUiKey, -1, 512);
        refreshIntervalMs = clamp(refreshIntervalMs, 500L, 60_000L);
        ensureIntervalTicks = clamp(ensureIntervalTicks, 5, 200);
        ensureLimitPerPass = clamp(ensureLimitPerPass, 1, 128);
        uiScale = clamp(uiScale, 0.6F, 1.75F);
        timeoutMs = clamp(timeoutMs, 3_000, 60_000);

        apiBaseUrl = sanitizeUrl(apiBaseUrl, "https://storage-api.catskin.space");
        pathUpload = sanitizePath(pathUpload, "/upload");
        pathSelect = sanitizePath(pathSelect, "/select");
        pathSelected = sanitizePath(pathSelected, "/selected");
        pathPublic = sanitizePath(pathPublic, "/public/");
        pathEvents = sanitizePath(pathEvents, "/events");
        if (traceLogging) {
            debugLogging = true;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String sanitizeUrl(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String sanitizePath(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        return trimmed;
    }
}

