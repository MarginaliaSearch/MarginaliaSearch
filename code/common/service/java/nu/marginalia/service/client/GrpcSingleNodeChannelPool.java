package nu.marginalia.service.client;

import com.google.common.collect.Sets;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.monitor.ServiceChangeMonitor;
import nu.marginalia.service.discovery.property.PartitionTraits;
import nu.marginalia.service.discovery.property.ServiceEndpoint.InstanceAddress;
import nu.marginalia.service.discovery.property.ServiceKey;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
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

    private final ServiceRegistryIf serviceRegistryIf;
    private final Function<InstanceAddress, ManagedChannel> channelConstructor;
    private final Function<ManagedChannel, STUB> stubConstructor;

    public GrpcSingleNodeChannelPool(ServiceRegistryIf serviceRegistryIf,
                                     ServiceKey<? extends PartitionTraits.Unicast> serviceKey,
                                     Function<InstanceAddress, ManagedChannel> channelConstructor,
                                     Function<ManagedChannel, STUB> stubConstructor)
            throws Exception
    {
        super(serviceKey);

        this.serviceRegistryIf = serviceRegistryIf;
        this.channelConstructor = channelConstructor;
        this.stubConstructor = stubConstructor;

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

    }

    // Mostly for testing
    public synchronized void stop() {
        for (var channel : channels.values()) {
            channel.closeHard();
        }
        channels.clear();
    }

    private class ConnectionHolder implements Comparable<ConnectionHolder> {
        private final AtomicReference<ManagedChannel> channel = new AtomicReference<>();
        private final InstanceAddress address;
        private volatile long lastError = Long.MIN_VALUE;
        private volatile long lastUsed = Long.MAX_VALUE;

        ConnectionHolder(InstanceAddress address) {
            this.address = address;
        }

        public ManagedChannel get() {
            var value = channel.get();

            lastUsed = System.currentTimeMillis();

            if (value != null) {
                return value;
            }

            try {
                logger.info(grpcMarker, "Creating channel for {} => {}", serviceKey, address);
                value = channelConstructor.apply(address);
                if (channel.compareAndSet(null, value)) {
                    return value;
                }
                else {
                    value.shutdown();
                    return channel.get();
                }
            }
            catch (Exception e) {
                logger.error(grpcMarker, "Failed to get channel for " + address, e);
                return null;
            }
        }

        public void close() {
            ManagedChannel mc = channel.getAndSet(null);
            if (mc != null) {
                mc.shutdown();
            }
        }
        public void closeHard() {
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

        /** Keep track of the last time this channel errored, up til 5 minutes */
        private boolean hasRecentError() {
            return System.currentTimeMillis() < lastError + Duration.ofMinutes(5).toMillis();
        }

        void flagError() {
            lastError = System.currentTimeMillis();
        }

        @Override
        public int compareTo(@NotNull GrpcSingleNodeChannelPool<STUB>.ConnectionHolder o) {
            // If one has recently errored and the other has not, the one that has not errored is preferred
            int diff = Boolean.compare(hasRecentError(), o.hasRecentError());
            if (diff != 0) return diff;

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
        final List<Exception> exceptions = new ArrayList<>();
        final List<ConnectionHolder> connectionHolders = new ArrayList<>(channels.values());

        // Sorting the channel list will give us a round-robin distribution of calls,
        // while preferring channels that have not errored recently
        Collections.sort(connectionHolders);

        for (var channel : connectionHolders) {
            try {
                return call.apply(stubConstructor.apply(channel.get()), arg);
            }
            catch (Exception e) {
                channel.flagError();

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
