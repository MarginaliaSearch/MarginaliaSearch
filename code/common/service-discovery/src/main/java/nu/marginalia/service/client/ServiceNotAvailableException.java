package nu.marginalia.service.client;

import nu.marginalia.service.id.ServiceId;

public class ServiceNotAvailableException extends RuntimeException {
    public ServiceNotAvailableException(ServiceId id, int node) {
        super(STR."Service \{id} not available on node \{node}");
    }
    public ServiceNotAvailableException(ServiceId id) {
        super(STR."Service \{id} not available");
    }
}
