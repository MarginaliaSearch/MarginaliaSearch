package nu.marginalia.service.client;

import io.grpc.ManagedChannel;
import lombok.SneakyThrows;
import nu.marginalia.service.NodeConfigurationWatcher;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.property.ServiceEndpoint;
import nu.marginalia.service.id.ServiceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/** A pool of gRPC channels for a service, with a separate channel for each node.
 * <p></p>
 * Manages broadcast-style request. */
public class GrpcMultiNodeChannelPool<STUB> {
    private final ConcurrentHashMap<Integer, GrpcSingleNodeChannelPool<STUB>> pools =
            new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(GrpcMultiNodeChannelPool.class);
    private final ExecutorService virtualExecutorService = Executors.newVirtualThreadPerTaskExecutor();
    private final ServiceRegistryIf serviceRegistryIf;
    private final ServiceId serviceId;
    private final Function<ServiceEndpoint.InstanceAddress<?>, ManagedChannel> channelConstructor;
    private final Function<ManagedChannel, STUB> stubConstructor;
    private final NodeConfigurationWatcher nodeConfigurationWatcher;

    @SneakyThrows
    public GrpcMultiNodeChannelPool(ServiceRegistryIf serviceRegistryIf,
                                    ServiceId serviceId,
                                    Function<ServiceEndpoint.InstanceAddress<?>, ManagedChannel> channelConstructor,
                                    Function<ManagedChannel, STUB> stubConstructor,
                                    NodeConfigurationWatcher nodeConfigurationWatcher) {
        this.serviceRegistryIf = serviceRegistryIf;
        this.serviceId = serviceId;
        this.channelConstructor = channelConstructor;
        this.stubConstructor = stubConstructor;
        this.nodeConfigurationWatcher = nodeConfigurationWatcher;
    }



    /** Get an API stub for the given node */
    public STUB apiForNode(int node) {
        return pools.computeIfAbsent(node, _ ->
            new GrpcSingleNodeChannelPool<>(
                    serviceRegistryIf,
                    serviceId,
                    new NodeSelectionStrategy.Just(node),
                    channelConstructor,
                    stubConstructor)
        ).api();
    }


    /** Invoke a function on each node, returning a list of futures in a terminal state, as per
     * ExecutorService$invokeAll */
    public <T> List<Future<T>> invokeAll(Function<STUB, Callable<T>> callF) throws InterruptedException {
        List<Callable<T>> calls = getEligibleNodes().stream()
                .mapMulti(this::passNodeIfOk)
                .map(callF)
                .toList();

        return virtualExecutorService.invokeAll(calls);
    }

    /** Invoke a function on each node, returning a stream of results */
    public <T> Stream<T> callEachSequential(Function<STUB, T> call) {
        return getEligibleNodes().stream()
                .mapMulti(this::passNodeIfOk)
                .map(call);
    }

    // Eat connectivity exceptions and log them when doing a broadcast-style calls
    private void passNodeIfOk(Integer nodeId, Consumer<STUB> consumer) {
        try {
            consumer.accept(apiForNode(nodeId));
        }
        catch (Exception ex) {
            logger.error("Error calling node {}", nodeId, ex);
        }
    }

    /** Get the list of nodes that are eligible for broadcast-style requests */
    public List<Integer> getEligibleNodes() {
        return nodeConfigurationWatcher.getQueryNodes();
    }

}
