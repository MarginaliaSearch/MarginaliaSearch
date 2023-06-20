package nu.marginalia.service;

public class ServiceHomeNotConfiguredException extends RuntimeException {

    public ServiceHomeNotConfiguredException() {
        super("WMSA_HOME environment variable not set");
    }
    public ServiceHomeNotConfiguredException(String message) {
        super(message);
    }
}
