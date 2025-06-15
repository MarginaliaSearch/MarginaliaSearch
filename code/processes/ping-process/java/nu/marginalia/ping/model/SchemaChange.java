package nu.marginalia.ping.model;

public enum SchemaChange {
    UNKNOWN,
    NO_CHANGE,
    HTTP_TO_HTTPS,
    HTTPS_TO_HTTP;

    public boolean isSignificant() {
        return this != NO_CHANGE && this != UNKNOWN;
    }
}
