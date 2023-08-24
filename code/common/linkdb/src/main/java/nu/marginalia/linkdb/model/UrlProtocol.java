package nu.marginalia.linkdb.model;

public enum UrlProtocol {
    HTTP,
    HTTPS;

    public static int encode(String str) {
        if ("http".equalsIgnoreCase(str)) {
            return HTTP.ordinal();
        }
        else if ("https".equalsIgnoreCase(str)) {
            return HTTPS.ordinal();
        }

        throw new IllegalArgumentException(str);
    }

    public static String decode(int ordinal) {
        return switch (values()[ordinal]) {
            case HTTP -> "http";
            case HTTPS -> "https";
        };
    };
}
