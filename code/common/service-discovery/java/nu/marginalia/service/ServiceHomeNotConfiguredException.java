package nu.marginalia.service;

public class ServiceHomeNotConfiguredException extends RuntimeException {
    public ServiceHomeNotConfiguredException(String message) {
        super(message);
    }
}
