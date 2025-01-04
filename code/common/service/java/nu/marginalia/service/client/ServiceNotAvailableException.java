package nu.marginalia.service.client;

import nu.marginalia.service.discovery.property.ServiceKey;

public class ServiceNotAvailableException extends RuntimeException {
    public ServiceNotAvailableException(ServiceKey<?> key) {
        super(key.toString());
    }

    @Override
    public StackTraceElement[] getStackTrace() { // Suppress stack trace
        return new StackTraceElement[0];
    }
}
