package nu.marginalia.service.discovery.monitor;

import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.ZkServiceRegistry;
import nu.marginalia.service.id.ServiceId;

public abstract class ServiceChangeMonitor implements ServiceMonitorIf {
    public final ServiceId serviceId;

    public ServiceChangeMonitor(ServiceId serviceId) {
        this.serviceId = serviceId;
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
