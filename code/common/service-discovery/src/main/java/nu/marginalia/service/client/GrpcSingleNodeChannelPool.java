package nu.marginalia.service.client;

import com.google.common.collect.Sets;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import lombok.SneakyThrows;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.monitor.ServiceChangeMonitor;
import nu.marginalia.service.discovery.property.ApiSchema;
import nu.marginalia.service.discovery.property.ServiceEndpoint.InstanceAddress;
import nu.marginalia.service.id.ServiceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/** A pool of gRPC channels for a service, with a separate channel for each node.
 * <p></p>
 * Manages unicast-style requests */
public class GrpcSingleNodeChannelPool<STUB> extends ServiceChangeMonitor {
    private final Map<InstanceAddress<?>, ManagedChannel> channels = new ConcurrentHashMap<>();
    private volatile Set<InstanceAddress<?>> routes = Set.of();

    private static final Logger logger = LoggerFactory.getLogger(GrpcSingleNodeChannelPool.class);

    private final ServiceRegistryIf serviceRegistryIf;
    private final ServiceId serviceId;
    private final NodeSelectionStrategy nodeSelectionStrategy;
    private final Function<InstanceAddress<?>, ManagedChannel> channelConstructor;
    private final Function<ManagedChannel, STUB> stubConstructor;

    @SneakyThrows
    public GrpcSingleNodeChannelPool(ServiceRegistryIf serviceRegistryIf,
                                     ServiceId serviceId,
                                     NodeSelectionStrategy nodeSelectionStrategy,
                                     Function<InstanceAddress<?>, ManagedChannel> channelConstructor,
                                     Function<ManagedChannel, STUB> stubConstructor) {
        super(serviceId);

        this.serviceRegistryIf = serviceRegistryIf;
        this.serviceId = serviceId;
        this.nodeSelectionStrategy = nodeSelectionStrategy;
        this.channelConstructor = channelConstructor;
        this.stubConstructor = stubConstructor;

        serviceRegistryIf.registerMonitor(this);

        onChange();
    }


    @Override
    public boolean onChange() {
        switch (nodeSelectionStrategy) {
            case NodeSelectionStrategy.Any() ->
                    serviceRegistryIf
                        .getServiceNodes(serviceId)
                        .forEach(this::refreshNode);
            case NodeSelectionStrategy.Just(int node) ->
                    refreshNode(node);
        }

        return true;
    }

    private void refreshNode(int node) {

        Set<InstanceAddress<?>> newRoutes = serviceRegistryIf.getEndpoints(ApiSchema.GRPC, serviceId, node);
        Set<InstanceAddress<?>> oldRoutes = routes;

        if (!oldRoutes.equals(newRoutes)) {
            // Find the routes that have been added or removed
            for (var route : Sets.symmetricDifference(oldRoutes, newRoutes)) {

                ManagedChannel oldChannel;

                if (newRoutes.contains(route)) {
                    logger.info(STR."Adding channel for \{serviceId.serviceName}-\{node} \{route.host()}:\{route.port()}");

                    var newChannel = channelConstructor.apply(route);
                    oldChannel = channels.put(route, newChannel);
                } else {
                    logger.info(STR."Removing channel for \{serviceId.serviceName}-\{node} \{route.host()}:\{route.port()}");

                    oldChannel = channels.remove(route);
                }

                if (oldChannel != null)
                    oldChannel.shutdown();
            }

            routes = newRoutes;
        }
    }

    public boolean hasChannel() {
        return !channels.isEmpty();
    }

    /** Get an API stub for the given node */
    public STUB api() {
        return stubConstructor.apply(getChannel());
    }

    /** Get the channel that is most ready to use */
    public ManagedChannel getChannel() {
        return channels
                .values()
                .stream()
                .min(this::compareChannelsByState)
                .orElseThrow(() -> new ServiceNotAvailableException(serviceId));
    }

    /** Sort the channels by how ready they are to use */
    private int compareChannelsByState(ManagedChannel a, ManagedChannel b) {
        var aState = a.getState(true);
        var bState = b.getState(true);

        if (aState == ConnectivityState.READY) return -1;
        if (bState == ConnectivityState.READY) return 1;
        if (aState == ConnectivityState.CONNECTING) return -1;
        if (bState == ConnectivityState.CONNECTING) return 1;
        if (aState == ConnectivityState.IDLE) return -1;
        if (bState == ConnectivityState.IDLE) return 1;

        return 0;
    }


}
