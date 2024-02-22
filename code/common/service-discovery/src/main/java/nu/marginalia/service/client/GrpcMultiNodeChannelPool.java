package nu.marginalia.service.client;

import io.grpc.ManagedChannel;
import lombok.SneakyThrows;
import nu.marginalia.service.NodeConfigurationWatcher;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.property.PartitionTraits;
import nu.marginalia.service.discovery.property.ServiceEndpoint;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiFunction;
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
    private final ServiceRegistryIf serviceRegistryIf;
    private final ServiceKey<? extends PartitionTraits.Multicast> serviceKey;
    private final Function<ServiceEndpoint.InstanceAddress, ManagedChannel> channelConstructor;
    private final Function<ManagedChannel, STUB> stubConstructor;
    private final NodeConfigurationWatcher nodeConfigurationWatcher;

    @SneakyThrows
    public GrpcMultiNodeChannelPool(ServiceRegistryIf serviceRegistryIf,
                                    ServiceKey<ServicePartition.Multi> serviceKey,
                                    Function<ServiceEndpoint.InstanceAddress, ManagedChannel> channelConstructor,
                                    Function<ManagedChannel, STUB> stubConstructor,
                                    NodeConfigurationWatcher nodeConfigurationWatcher) {
        this.serviceRegistryIf = serviceRegistryIf;
        this.serviceKey = serviceKey;
        this.channelConstructor = channelConstructor;
        this.stubConstructor = stubConstructor;
        this.nodeConfigurationWatcher = nodeConfigurationWatcher;

        // Warm up the pool to reduce latency for the initial request
        for (var node : nodeConfigurationWatcher.getQueryNodes()) {
            getPoolForNode(node);
        }
    }

    private GrpcSingleNodeChannelPool<STUB> getPoolForNode(int node) {
        return pools.computeIfAbsent(node, _ ->
                new GrpcSingleNodeChannelPool<>(
                        serviceRegistryIf,
                        serviceKey.forPartition(ServicePartition.partition(node)),
                        channelConstructor,
                        stubConstructor));
    }


    /** Get the list of nodes that are eligible for broadcast-style requests */
    public List<Integer> getEligibleNodes() {
        return nodeConfigurationWatcher.getQueryNodes();
    }

    public <T, I> CallBuilderBase<T, I> call(BiFunction<STUB, I, T> method) {
        return new CallBuilderBase<>(method);
    }

    public class CallBuilderBase<T, I> {
        private final BiFunction<STUB, I, T> method;

        private CallBuilderBase(BiFunction<STUB, I, T> method) {
            this.method = method;
        }

        public GrpcSingleNodeChannelPool<STUB>.CallBuilderBase<T, I> forNode(int node) {
            return getPoolForNode(node).call(method);
        }

        public List<T> run(I arg) {
            return getEligibleNodes().stream()
                    .map(node -> getPoolForNode(node).call(method).run(arg))
                    .toList();
        }

        public CallBuilderAsync<T, I> async(ExecutorService service) {
            return new CallBuilderAsync<>(service, method);
        }
    }

    public class CallBuilderAsync<T, I> {
        private final Executor executor;
        private final BiFunction<STUB, I, T> method;

        public CallBuilderAsync(Executor executor, BiFunction<STUB, I, T> method) {
            this.executor = executor;
            this.method = method;
        }

        public CompletableFuture<List<T>> runAll(I arg) {
            var futures = getEligibleNodes().stream()
                    .map(GrpcMultiNodeChannelPool.this::getPoolForNode)
                    .map(pool ->
                            pool.call(method)
                                    .async(executor)
                                    .run(arg)
                    ).toList();

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
        }

        public List<CompletableFuture<T>> runEach(I arg) {
            return getEligibleNodes().stream()
                    .map(GrpcMultiNodeChannelPool.this::getPoolForNode)
                    .map(pool ->
                            pool.call(method)
                                    .async(executor)
                                    .run(arg)
                    ).toList();

        }
    }
}
