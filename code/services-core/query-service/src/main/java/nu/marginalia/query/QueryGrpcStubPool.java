package nu.marginalia.query;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import nu.marginalia.query.svc.NodeConfigurationWatcher;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Stream;

public abstract class QueryGrpcStubPool<STUB> {
    protected record ServiceAndNode(String service, int node) {
        public String getHostName() {
            return service+"-"+node;
        }
    }

    private final NodeConfigurationWatcher nodeConfigurationWatcher;
    private final Map<ServiceAndNode, ManagedChannel> channels = new ConcurrentHashMap<>();
    private final Map<ServiceAndNode, STUB> actorRpcApis = new ConcurrentHashMap<>();
    private final ExecutorService virtualExecutorService = Executors.newVirtualThreadPerTaskExecutor();

    QueryGrpcStubPool(NodeConfigurationWatcher nodeConfigurationWatcher) {
        this.nodeConfigurationWatcher = nodeConfigurationWatcher;
    }

    /** Get an API stub for the given node */
    public STUB indexApi(int node) {
        var san = new ServiceAndNode("index-service", node);
        return actorRpcApis.computeIfAbsent(san, n ->
                createStub(channels.computeIfAbsent(san, this::createChannel))
        );
    }

    protected ManagedChannel createChannel(ServiceAndNode serviceAndNode) {
        return ManagedChannelBuilder.forAddress(serviceAndNode.getHostName(), 81).usePlaintext().build();
    }

    /** Invoke a function on each node, returning a list of futures in a terminal state, as per
     * ExecutorService$invokeAll */
    public <T> List<Future<T>> invokeAll(Function<STUB, Callable<T>> callF) throws InterruptedException {
        List<Callable<T>> calls = nodeConfigurationWatcher.getQueryNodes().stream()
                .map(id -> callF.apply(indexApi(id)))
                .toList();

        return virtualExecutorService.invokeAll(calls);
    }

    /** Invoke a function on each node, returning a stream of results */
    public <T> Stream<T> callEachSequential(Function<STUB, T> call) {
        return nodeConfigurationWatcher.getQueryNodes().stream()
                .map(id -> call.apply(indexApi(id)));
    }


    /** Create a stub for the given channel, this is an operation
     * that needs to be implemented for the particular API this
     * pool is intended for
     */
    public abstract STUB createStub(ManagedChannel channel);

}
