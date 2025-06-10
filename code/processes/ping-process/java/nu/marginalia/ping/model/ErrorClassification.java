package nu.marginalia.ping.model;

public enum ErrorClassification {
    NONE,
    TIMEOUT,
    SSL_ERROR,
    DNS_ERROR,
    CONNECTION_ERROR,
    HTTP_CLIENT_ERROR,
    HTTP_SERVER_ERROR,
    UNKNOWN
}
