package nu.marginalia.service.discovery.monitor;

import nu.marginalia.service.discovery.property.ServiceKey;

public abstract class ServiceChangeMonitor implements ServiceMonitorIf {
    public final ServiceKey<?> serviceKey;

    public ServiceChangeMonitor(ServiceKey<?> key) {
        this.serviceKey = key;
    }

    public abstract boolean onChange();
    public ServiceKey<?> getKey() {
        return serviceKey;
    }

}
