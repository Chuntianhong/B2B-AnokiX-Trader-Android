package com.anokix.traderapp.network;

/**
 * Normalises media URLs returned by the API before loading images or video.
 */
public final class MediaUrls {

    private MediaUrls() {
    }

    public static String resolve(String url) {
        if (url == null) return null;
        url = url.trim();
        if (url.isEmpty()) return null;

        // Some responses wrap URLs in quotes; VideoView treats that as a local path.
        if ((url.startsWith("\"") && url.endsWith("\""))
                || (url.startsWith("'") && url.endsWith("'"))) {
            url = url.substring(1, url.length() - 1).trim();
        }
        if (url.isEmpty()) return null;

        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        String base = ApiClient.BASE_URL;
        if (url.startsWith("/")) {
            return trimTrailingSlash(base) + url;
        }
        return base + url;
    }

    private static String trimTrailingSlash(String base) {
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
