package nu.marginalia.service.client;

import com.google.common.collect.Sets;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.prometheus.metrics.core.metrics.Counter;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.monitor.ServiceChangeMonitor;
import nu.marginalia.service.discovery.property.PartitionTraits;
import nu.marginalia.service.discovery.property.ServiceEndpoint.InstanceAddress;
import nu.marginalia.service.discovery.property.ServiceKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

/** A pool of gRPC channels for a service, with a separate channel for each node.
 * <p></p>
 * Manages unicast-style requests */
public class GrpcSingleNodeChannelPool<STUB> extends ServiceChangeMonitor {
    private final Map<InstanceAddress, ConnectionHolder> channels = new ConcurrentHashMap<>();

    private final Marker grpcMarker = MarkerFactory.getMarker("GRPC");
    private static final Logger logger = LoggerFactory.getLogger(GrpcSingleNodeChannelPool.class);

    private static final Counter requestCounter = Counter.builder().name("wmsa_rpc_requests")
            .help("Request count")
            .labelNames("serviceKey")
            .build();

    private static final Counter errorCounter = Counter.builder().name("wmsa_rpc_errors")
            .help("Error count")
            .labelNames("serviceKey")
            .build();

    private final ServiceRegistryIf serviceRegistryIf;
    private final Function<InstanceAddress, ManagedChannel> channelConstructor;
    private final Function<ManagedChannel, STUB> defaultStubConstructor;

    public GrpcSingleNodeChannelPool(ServiceRegistryIf serviceRegistryIf,
                                     ServiceKey<? extends PartitionTraits.Unicast> serviceKey,
                                     Function<InstanceAddress, ManagedChannel> channelConstructor,
                                     Function<ManagedChannel, STUB> stubConstructor)
            throws Exception
    {
        super(serviceKey);

        this.serviceRegistryIf = serviceRegistryIf;
        this.channelConstructor = channelConstructor;
        this.defaultStubConstructor = stubConstructor;

        serviceRegistryIf.registerMonitor(this);

        onChange();
    }


    @Override
    public synchronized void onChange() {
        Set<InstanceAddress> newRoutes = new HashSet<>(serviceRegistryIf.getEndpoints(serviceKey));
        Set<InstanceAddress> oldRoutes = new HashSet<>(channels.keySet());

        // Find the routes that have been added or removed
        for (var route : Sets.symmetricDifference(oldRoutes, newRoutes)) {
            ConnectionHolder oldChannel;
            if (newRoutes.contains(route)) {
                logger.info(grpcMarker, "Adding route {} => {}", serviceKey, route);
                oldChannel = channels.put(route, new ConnectionHolder(route));
            } else {
                logger.info(grpcMarker, "Expelling route {} => {}", serviceKey, route);
                oldChannel = channels.remove(route);
            }
            if (oldChannel != null) {
                oldChannel.close();
            }
        }

        notifyAll();
    }

    // Mostly for testing
    public synchronized void stop() {
        for (var channel : channels.values()) {
            channel.closeHard();
        }
        channels.clear();
    }

    public class ConnectionHolder implements Comparable<ConnectionHolder> {
        private final AtomicReference<ManagedChannel> channel = new AtomicReference<>();
        private final InstanceAddress address;

        private volatile long lastError = System.nanoTime() - Duration.ofMinutes(10).toNanos();
        private volatile long lastUsed = System.nanoTime();
        private volatile boolean closed = false;

        ConnectionHolder(InstanceAddress address) {
            this.address = address;
        }

        @Nullable
        public ManagedChannel get() {
            ManagedChannel value;

            lastUsed = System.nanoTime();

            if ((value = channel.get()) != null) {
                // There is a small race condition in this branch, where value may be
                // closed after we enter this branch, which is not possible to prevent.
                //
                // Caller will just have to deal with ManagedChannels on very rare instances
                // being in weird states.
                return value;
            }
            else if (closed) {
                return null;
            }

            logger.info(grpcMarker, "Creating channel for {} => {}", serviceKey, address);

            try {
                value = channelConstructor.apply(address);

                // Handle unlikely but possible race scenario where multiple callers
                // compete to set 'channel'
                if (channel.compareAndSet(null, value)) {
                    if (closed) {
                        // Even more unlikely A->B->A case
                        value.shutdown();
                        return null;
                    }
                    return value;
                }
                else { // Close the superfluous channel we just created
                    value.shutdown();
                    return channel.get();
                }
            }
            catch (Exception e) {
                // ensure we don't retry this channel immediately
                lastError = System.nanoTime();

                logger.error(grpcMarker, "Failed to get channel for " + address, e);
                return null;
            }
        }

        void close() {
            closed = true;

            ManagedChannel mc = channel.getAndSet(null);
            if (mc != null) {
                mc.shutdown();
            }
        }

        void closeHard() {
            closed = true;

            ManagedChannel mc = channel.getAndSet(null);
            if (mc != null) {
                mc.shutdownNow();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConnectionHolder that = (ConnectionHolder) o;
            return Objects.equals(address, that.address);
        }


        @Override
        public int hashCode() {
            return Objects.hash(address);
        }

        /** Keep track of the last time this channel errored, up til 1 minute */
        private boolean hasRecentError() {
            return hasErrorSince(Duration.ofMinutes(1));
        }

        public boolean hasErrorSince(Duration duration) {
            return System.nanoTime() - lastError <  duration.toNanos();
        }

        public void flagError() {
            lastError = System.nanoTime();
        }

        public boolean hasConnection() {
            return channel.get() != null && !closed;
        }

        @Override
        public int compareTo(@NotNull GrpcSingleNodeChannelPool<STUB>.ConnectionHolder o) {
            int diff = Boolean.compare(hasConnection(), o.hasConnection());
            if (diff != 0) return -diff; // prefer true

            diff = Boolean.compare(hasRecentError(), o.hasRecentError());
            if (diff != 0) return diff; // prefer false

            // If no error has been recorded (or both have recent errors), round-robin between the options
            return Long.compare(lastUsed, o.lastUsed);
        }

    }



    public boolean hasChannel() {
        return !channels.isEmpty();
    }

    public synchronized boolean awaitChannel(Duration timeout) throws InterruptedException {
        if (hasChannel()) return true;

        final long endTime = System.currentTimeMillis() + timeout.toMillis();

        while (!hasChannel()) {
            long timeLeft = endTime - System.currentTimeMillis();
            if (timeLeft <= 0) return false;
            this.wait(timeLeft);
        }

        return hasChannel();
    }

    private <T, I> T call(BiFunction<STUB, I, T> call, I arg) throws RuntimeException {
        return call(defaultStubConstructor, call, arg);
    }


    public List<ConnectionHolder> getConnectionHolders() {
        return channels.values().stream().sorted().toList();
    }

    public <T, I> T call(Function<ManagedChannel, STUB> stubConstructor,
                          BiFunction<STUB, I, T> call,
                          I arg) throws RuntimeException {
        final List<Exception> exceptions = new ArrayList<>();
        final List<ConnectionHolder> connectionHolders = getConnectionHolders();

        final String serviceKeyStr = serviceKey.toString();

        for (var holder : connectionHolders) {
            try {
                ManagedChannel channel = holder.get();
                if (null == channel)
                    continue;

                var ret = call.apply(stubConstructor.apply(channel), arg);

                requestCounter.labelValues(serviceKeyStr).inc();

                return ret;
            }
            catch (Exception e) {
                holder.flagError();
                errorCounter.labelValues(serviceKeyStr).inc();

                exceptions.add(e);
            }
        }

        for (var e : exceptions) {
            if (e instanceof StatusRuntimeException se) {
                throw se; // Re-throw SRE as-is
            }

            // If there are other exceptions, log them
            logger.error(grpcMarker, "Failed to call service {}", serviceKey, e);
        }

        throw new ServiceNotAvailableException(serviceKey);
    }

    private <T, I> List<Future<T>> broadcast(BiFunction<STUB, I, T> call, I arg) throws RuntimeException {
        final List<Exception> exceptions = new ArrayList<>();
        List<Future<T>> ret = new ArrayList<>();

        String serviceKeyStr = serviceKey.toString();
        for (var holder : channels.values()) {
            try {
                ManagedChannel channel = holder.get();
                if (channel == null)
                    continue;

                ret.add(CompletableFuture.completedFuture(call.apply(defaultStubConstructor.apply(channel), arg)));
                requestCounter.labelValues(serviceKeyStr).inc();
            }
            catch (Exception e) {
                ret.add(CompletableFuture.failedFuture(e));
                holder.flagError();
                exceptions.add(e);
            }
        }

        for (var e : exceptions) {
            if (e instanceof StatusRuntimeException se) {
                throw se; // Re-throw SRE as-is
            }

            errorCounter.labelValues(serviceKey.toString()).inc();

            // If there are other exceptions, log them
            logger.error(grpcMarker, "Failed to call service {}", serviceKey, e);
        }

        return ret;
    }

    /** Create a call for the given method on the given node.
     * This is a fluent method, so you can chain it with other
     * methods to specify the node and arguments */
    public <T, I> CallBuilderBase<T, I> call(BiFunction<STUB, I, T> method) {
        return new CallBuilderBase<>(method);
    }

    public class CallBuilderBase<T, I> {
        private final BiFunction<STUB, I, T> method;
        private CallBuilderBase(BiFunction<STUB, I, T> method) {
            this.method = method;
        }

        /** Execute the call in a blocking manner */
        public T run(I arg) {
            return call(method, arg);
        }

        /** Create an asynchronous call using the provided executor */
        public CallBuilderAsync<T, I> async(Executor executor) {
            return new CallBuilderAsync<>(executor, method);
        }

        /** Send message to all partitions */
        public CallBuilderBroadcast<T, I> broadcast() {
            return new CallBuilderBroadcast<>(method);
        }
    }

    public class CallBuilderBroadcast<T, I> {
        private final BiFunction<STUB, I, T> method;
        private CallBuilderBroadcast(BiFunction<STUB, I, T> method) {
            this.method = method;
        }

        /** Execute the call in a blocking manner */
        public List<Future<T>> run(I arg) {
            return broadcast(method, arg);
        }
    }

    public class CallBuilderAsync<T, I> {
        private final Executor executor;
        private final BiFunction<STUB, I, T> method;

        public CallBuilderAsync(Executor executor, BiFunction<STUB, I, T> method) {
            this.executor = executor;
            this.method = method;
        }

        /** Execute the call in an asynchronous manner */
        public CompletableFuture<T> run(I arg) {
            return CompletableFuture.supplyAsync(() -> call(method, arg), executor);
        }

        /** Execute the call in an asynchronous manner for each of the given arguments */
        public CompletableFuture<List<T>> runFor(List<I> args) {
            List<CompletableFuture<T>> results = new ArrayList<>();
            for (var arg : args) {
                results.add(CompletableFuture.supplyAsync(() -> call(method, arg), executor));
            }
            return CompletableFuture.allOf(results.toArray(new CompletableFuture[0]))
                    .thenApply(v -> results.stream().map(CompletableFuture::join).toList());
        }

        /** Execute the call in an asynchronous manner for each of the given arguments */
        public CompletableFuture<List<T>> runFor(I... args) {
            return runFor(List.of(args));
        }
    }
}
