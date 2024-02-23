package nu.marginalia.service.client;

import nu.marginalia.service.discovery.property.ServiceKey;

public class ServiceNotAvailableException extends RuntimeException {
    public ServiceNotAvailableException(ServiceKey<?> key) {
        super(STR."Service \{key} not available");
    }
}
