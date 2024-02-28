package nu.marginalia.service.discovery.monitor;


import nu.marginalia.service.discovery.property.ServiceKey;

public interface ServiceMonitorIf {
    /** Called when the monitored service has changed.
     * @return true if the monitor is to be refreshed
     */
    boolean onChange();
    ServiceKey<?> getKey();

}
