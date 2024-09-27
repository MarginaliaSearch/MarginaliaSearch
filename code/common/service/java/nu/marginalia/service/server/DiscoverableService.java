package nu.marginalia.service.server;

import io.grpc.BindableService;

public interface DiscoverableService extends BindableService {
    /** Signal whether the service is enabled or not.
     * Disabling the service will prevent it from being registered with the service registry.
     * This may be useful for services that should only be run in certain circumstances, on specific nodes, etc.
     * */
    default boolean shouldRegisterService() {
        return true;
    }
}
