package nu.marginalia.service.discovery.monitor;

import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.ZkServiceRegistry;
import nu.marginalia.service.id.ServiceId;

public abstract class ServiceGrpcEndpointChangeMonitor implements ServiceMonitorIf {
    public final ServiceId serviceId;
    public final int node;
    public ServiceGrpcEndpointChangeMonitor(ServiceId serviceId, int node) {
        this.serviceId = serviceId;
        this.node = node;
    }

    public abstract boolean onChange();

    public void register(ServiceRegistryIf registry) throws Exception {
        if (registry instanceof ZkServiceRegistry zkServiceRegistry) {
            zkServiceRegistry.registerMonitor(this);
        }
        else {
            registry.registerMonitor(this);
        }
    }
}
