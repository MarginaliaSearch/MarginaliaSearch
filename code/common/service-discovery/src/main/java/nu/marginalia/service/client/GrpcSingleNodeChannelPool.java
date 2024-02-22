package nu.marginalia.service.client;

import com.google.common.collect.Sets;
import io.grpc.ManagedChannel;
import lombok.SneakyThrows;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.monitor.ServiceChangeMonitor;
import nu.marginalia.service.discovery.property.PartitionTraits;
import nu.marginalia.service.discovery.property.ServiceEndpoint.InstanceAddress;
import nu.marginalia.service.discovery.property.ServiceKey;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

/** A pool of gRPC channels for a service, with a separate channel for each node.
 * <p></p>
 * Manages unicast-style requests */
public class GrpcSingleNodeChannelPool<STUB> extends ServiceChangeMonitor {
    private final Map<InstanceAddress, ConnectionHolder> channels = new ConcurrentHashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(GrpcSingleNodeChannelPool.class);

    private final ServiceRegistryIf serviceRegistryIf;
    private final Function<InstanceAddress, ManagedChannel> channelConstructor;
    private final Function<ManagedChannel, STUB> stubConstructor;


    @SneakyThrows
    public GrpcSingleNodeChannelPool(ServiceRegistryIf serviceRegistryIf,
                                     ServiceKey<? extends PartitionTraits.Unicast> serviceKey,
                                     Function<InstanceAddress, ManagedChannel> channelConstructor,
                                     Function<ManagedChannel, STUB> stubConstructor) {
        super(serviceKey);

        this.serviceRegistryIf = serviceRegistryIf;
        this.channelConstructor = channelConstructor;
        this.stubConstructor = stubConstructor;

        serviceRegistryIf.registerMonitor(this);

        onChange();

        awaitChannel(Duration.ofSeconds(5));
    }


    @Override
    public synchronized boolean onChange() {
        Set<InstanceAddress> newRoutes = serviceRegistryIf.getEndpoints(serviceKey);
        Set<InstanceAddress> oldRoutes = new HashSet<>(channels.keySet());

        // Find the routes that have been added or removed
        for (var route : Sets.symmetricDifference(oldRoutes, newRoutes)) {
            ConnectionHolder oldChannel;
            if (newRoutes.contains(route)) {
                logger.info("Adding route {}", route);
                oldChannel = channels.put(route, new ConnectionHolder(route));
            } else {
                logger.info("Expelling route {}", route);
                oldChannel = channels.remove(route);
            }
            if (oldChannel != null) {
                oldChannel.close();
            }
        }

        return true;
    }

    private class ConnectionHolder {
        private final AtomicReference<ManagedChannel> channel = new AtomicReference<>();
        private final InstanceAddress address;

        ConnectionHolder(InstanceAddress address) {
            this.address = address;
        }

        public ManagedChannel get() {
            var value = channel.get();
            if (value != null) {
                return value;
            }

            try {
                logger.info("Creating channel for {}:{}", serviceKey, address);
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
                logger.error(STR."Failed to get channel for \{address}", e);
                return null;
            }
        }

        public void close() {
            ManagedChannel mc = channel.getAndSet(null);
            if (mc != null) {
                mc.shutdown();
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

        // Randomize the order of the connection holders to spread out the load
        Collections.shuffle(connectionHolders);

        for (var channel : connectionHolders) {
            try {
                return call.apply(stubConstructor.apply(channel.get()), arg);
            }
            catch (Exception e) {
                exceptions.add(e);
            }
        }

        for (var e : exceptions) {
            logger.error("Failed to call service {}", serviceKey, e);
        }

        throw new ServiceNotAvailableException(serviceKey);
    }

    public <T, I> CallBuilderBase<T, I> call(BiFunction<STUB, I, T> method) {
        return new CallBuilderBase<>(method);
    }

    public class CallBuilderBase<T, I> {
        private final BiFunction<STUB, I, T> method;
        private CallBuilderBase(BiFunction<STUB, I, T> method) {
            this.method = method;
        }

        public T run(I arg) {
            return call(method, arg);
        }

        public List<T> runFor(I... args) {
            return runFor(List.of(args));
        }

        public List<T> runFor(List<I> args) {
            List<T> results = new ArrayList<>();
            for (var arg : args) {
                results.add(call(method, arg));
            }
            return results;
        }
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

        public CompletableFuture<T> run(I arg) {
            return CompletableFuture.supplyAsync(() -> call(method, arg), executor);
        }
        public CompletableFuture<List<T>> runFor(List<I> args) {
            List<CompletableFuture<T>> results = new ArrayList<>();
            for (var arg : args) {
                results.add(CompletableFuture.supplyAsync(() -> call(method, arg), executor));
            }
            return CompletableFuture.allOf(results.toArray(new CompletableFuture[0]))
                    .thenApply(v -> results.stream().map(CompletableFuture::join).toList());
        }
        public CompletableFuture<List<T>> runFor(I... args) {
            return runFor(List.of(args));
        }
    }
}
