package nu.marginalia.service.discovery.monitor;

import nu.marginalia.service.discovery.ServiceRegistryIf;

public interface ServiceMonitorIf {
    /** Called when the monitored service has changed.
     * @return true if the monitor is to be refreshed
     */
    boolean onChange();

    /** Register this monitor with the given registry.
     * It is preferred to use {@link ServiceRegistryIf}'s
     * registerMonitor function.
     * */
    void register(ServiceRegistryIf registry) throws Exception;
}
