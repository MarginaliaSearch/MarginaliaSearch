package nu.marginalia.service.discovery.monitor;


import nu.marginalia.service.discovery.property.ServiceKey;

public interface ServiceMonitorIf {
    void onChange();
    ServiceKey<?> getKey();

}
