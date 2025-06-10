package nu.marginalia.ping.ssl;

public enum PkixValidationError {
    SAN_MISMATCH,
    EXPIRED,
    NOT_YET_VALID,
    PATH_VALIDATION_FAILED,
    INVALID_PKIX_PARAMETERS,
    UNKNOWN,
    UNSPECIFIED_HOST_ERROR;
}
