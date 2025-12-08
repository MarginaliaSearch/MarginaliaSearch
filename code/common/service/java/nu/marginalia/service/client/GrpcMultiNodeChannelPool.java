package nu.marginalia.service.client;

import io.grpc.ManagedChannel;
import nu.marginalia.service.NodeConfigurationWatcher;
import nu.marginalia.service.NodeConfigurationWatcherIf;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.property.PartitionTraits;
import nu.marginalia.service.discovery.property.ServiceEndpoint;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;

/** A pool of gRPC channels for a service, with a separate channel for each node.
 * <p></p>
 * Manages broadcast-style request. */
public class GrpcMultiNodeChannelPool<STUB> {
    private final ConcurrentHashMap<Integer, GrpcSingleNodeChannelPool<STUB>> pools =
            new ConcurrentHashMap<>();

    private final ServiceRegistryIf serviceRegistryIf;
    private final ServiceKey<? extends PartitionTraits.Multicast> serviceKey;
    private final Function<ServiceEndpoint.InstanceAddress, ManagedChannel> channelConstructor;
    private final Function<ManagedChannel, STUB> stubConstructor;
    private final NodeConfigurationWatcherIf nodeConfigurationWatcher;

    public GrpcMultiNodeChannelPool(ServiceRegistryIf serviceRegistryIf,
                                    ServiceKey<ServicePartition.Multi> serviceKey,
                                    Function<ServiceEndpoint.InstanceAddress, ManagedChannel> channelConstructor,
                                    Function<ManagedChannel, STUB> stubConstructor,
                                    NodeConfigurationWatcherIf nodeConfigurationWatcher) {
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
        return pools.computeIfAbsent(node, this::newSingleChannelPool);
    }

    private GrpcSingleNodeChannelPool<STUB> newSingleChannelPool(int node) {
        try {
            return new GrpcSingleNodeChannelPool<>(
                    serviceRegistryIf,
                    serviceKey.forPartition(ServicePartition.partition(node)),
                    channelConstructor,
                    stubConstructor);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Get the list of nodes that are eligible for broadcast-style requests */
    public List<Integer> getEligibleNodes() {
        return nodeConfigurationWatcher.getQueryNodes();
    }

    /** Return the number of nodes that are eligible for broadcast-style requests */
    public int getNumNodes() {
        return nodeConfigurationWatcher.getQueryNodes().size();
    }

    /** Create a new call builder for the given method.  This is a fluent-style
     * method, where you can chain calls to specify how to run the method.
     * <p></p>
     * Example:
     * <code><pre>
     *     var results = channelPool.call(AStub:someMethod)
     *                   .async(someExecutor)
     *                   .runAll(argumentToSomeMethod);
     * </pre></code>
     * */
    public <T, I> CallBuilderBase<T, I> call(BiFunction<STUB, I, T> method) {
        return new CallBuilderBase<>(method);
    }

    public class CallBuilderBase<T, I> {
        private final BiFunction<STUB, I, T> method;

        private CallBuilderBase(BiFunction<STUB, I, T> method) {
            this.method = method;
        }

        /** Create a call for the given method on the given node */
        public GrpcSingleNodeChannelPool<STUB>.CallBuilderBase<T, I> forNode(int node) {
            return getPoolForNode(node).call(method);
        }

        /** Run the given method on each node, returning a list of results.
         * This is a blocking method, where each call will be made in sequence */
        public List<T> run(I arg) {
            return getEligibleNodes().stream()
                    .map(node -> getPoolForNode(node).call(method).run(arg))
                    .toList();
        }

        /** Generate an async call builder for the given method */
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

        /** Run the given method on each node, returning a future of a list of results */
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

        /** Run the given method on each node, returning a list of futures. */
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
