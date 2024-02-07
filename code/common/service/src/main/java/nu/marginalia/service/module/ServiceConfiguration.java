package nu.marginalia.service.module;

import nu.marginalia.service.id.ServiceId;

import java.util.UUID;

/**
 * Configuration object for a service. This is a guice-injectable object
 * intended to keep down the amount of named bindings.
 *
 * @param serviceId   - service descriptor
 * @param node - always 0 for now, for future service partitioning
 * @param host - the bind address of the service
 * @param port - main port of the service
 * @param metricsPort - prometheus metrics server port
 * @param instanceUuid - unique identifier for this instance of the service
 */
public record ServiceConfiguration(ServiceId serviceId,
                                   int node,
                                   String host,
                                   int port,
                                   int metricsPort,
                                   UUID instanceUuid) {
    public String serviceName() {
        return serviceId.serviceName;
    }
}
