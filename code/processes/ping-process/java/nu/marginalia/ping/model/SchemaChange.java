package nu.marginalia.ping.model;

public enum SchemaChange {
    UNKNOWN,
    NONE,
    HTTP_TO_HTTPS,
    HTTPS_TO_HTTP;

    public boolean isSignificant() {
        return this != NONE && this != UNKNOWN;
    }
}
