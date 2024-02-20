package nu.marginalia.service.discovery;

import nu.marginalia.service.discovery.monitor.*;
import nu.marginalia.service.discovery.property.ApiSchema;
import nu.marginalia.service.discovery.property.ServiceEndpoint;
import static nu.marginalia.service.discovery.property.ServiceEndpoint.*;
import nu.marginalia.service.id.ServiceId;

import java.util.Set;
import java.util.UUID;

/** A service registry that allows services to register themselves and
 * be discovered by other services on the network.
 */
public interface ServiceRegistryIf {
    /**
     * Register a service with the registry.
     * <p></p>
     * Once the instance has announced itself with {@link #announceInstance(ServiceId id, int node, UUID instanceUUID) announceInstance(...)},
     * the service will be available for discovery with {@link #getEndpoints(ApiSchema schema, ServiceId id, int node) getEndpoints(...)}.
     *
     * @param schema          the API schema
     * @param id              the service identifier
     * @param node            the node number
     * @param instanceUUID    the unique UUID of the instance
     * @param externalAddress the public address of the service
     */
    ServiceEndpoint registerService(ApiSchema schema,
                                    ServiceId id,
                                    int node,
                                    UUID instanceUUID,
                                    String externalAddress) throws Exception;

    /** Let the world know that the service is running
     * and ready to accept requests. */
    void announceInstance(ServiceId id, int node, UUID instanceUUID);

    /** Return all nodes that are running for the specified service. */
    Set<Integer> getServiceNodes(ServiceId id);

    /** At the discretion of the implementation, provide a port that is unique
     * across (externalHost, serviceId, schema, node).  It may be randomly selected
     * or hard-coded or some combination of behaviors.
     */
    int requestPort(String externalHost,
                    ApiSchema schema,
                    ServiceId id,
                    int node);

    /** Get all endpoints for the service on the specified node and schema. */
    Set<InstanceAddress<? extends ServiceEndpoint>>
        getEndpoints(ApiSchema schema, ServiceId id, int node);

    /** Register a monitor to be notified when the service registry changes.
     * <p></p>
     * {@link ServiceMonitorIf#onChange()} will be called when the registry changes.
     * Spurious calls to {@link ServiceMonitorIf#onChange()} are allowed depending
     * on the implementation.
     * <p></p>
     * Behavior of the monitor depends on the implementation of the registry, and the
     * monitor type.
     * <ul>
     * <li>{@link ServiceChangeMonitor} is notified when any node for the service changes.</li>
     * <li>{@link ServiceNodeChangeMonitor} is notified when a specific node for the service changes.</li>
     * <li>{@link ServiceRestEndpointChangeMonitor} is notified when the REST endpoints for the specified node service changes.</li>
     * <li>{@link ServiceGrpcEndpointChangeMonitor} is notified when the gRPC endpoints for the specified node service changes.</li>
     * </ul>
     * */
    void registerMonitor(ServiceMonitorIf monitor) throws Exception;
}
