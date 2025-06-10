package nu.marginalia.ping.model;

public enum AvailabilityOutageType {
    NONE,
    TIMEOUT,
    SSL_ERROR,
    DNS_ERROR,
    CONNECTION_ERROR,
    HTTP_CLIENT_ERROR,
    HTTP_SERVER_ERROR,
    UNKNOWN;

    public static AvailabilityOutageType fromErrorClassification(ErrorClassification errorClassification) {
        if (null == errorClassification) {
            return UNKNOWN;
        }

        return switch (errorClassification) {
            case NONE -> NONE;
            case TIMEOUT -> TIMEOUT;
            case SSL_ERROR -> SSL_ERROR;
            case DNS_ERROR -> DNS_ERROR;
            case CONNECTION_ERROR -> CONNECTION_ERROR;
            case HTTP_CLIENT_ERROR -> HTTP_CLIENT_ERROR;
            case HTTP_SERVER_ERROR -> HTTP_SERVER_ERROR;
            case UNKNOWN -> UNKNOWN;
        };
    }
}
