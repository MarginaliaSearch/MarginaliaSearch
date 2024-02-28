package nu.marginalia.service.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import nu.marginalia.service.NamedExecutorFactory;
import nu.marginalia.service.NodeConfigurationWatcher;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.property.PartitionTraits;
import nu.marginalia.service.discovery.property.ServiceEndpoint.InstanceAddress;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;

import java.util.concurrent.Executor;
import java.util.function.Function;

@Singleton
public class GrpcChannelPoolFactory {

    private final NodeConfigurationWatcher nodeConfigurationWatcher;
    private final ServiceRegistryIf serviceRegistryIf;
    private static final Executor executor = NamedExecutorFactory.createFixed("gRPC-Channel-Pool",
            Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 2, 16));
    private static final Executor offloadExecutor = NamedExecutorFactory.createFixed("gRPC-Offload-Pool",
            Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 2, 16));

    @Inject
    public GrpcChannelPoolFactory(NodeConfigurationWatcher nodeConfigurationWatcher,
                                  ServiceRegistryIf serviceRegistryIf)
    {
        this.nodeConfigurationWatcher = nodeConfigurationWatcher;
        this.serviceRegistryIf = serviceRegistryIf;
    }

    /** Create a new multi-node channel pool for the given service. */
    public <STUB> GrpcMultiNodeChannelPool<STUB> createMulti(ServiceKey<ServicePartition.Multi> key,
                                                             Function<ManagedChannel, STUB> stubConstructor)
    {
        return new GrpcMultiNodeChannelPool<>(serviceRegistryIf,
                key,
                this::createChannel,
                stubConstructor,
                nodeConfigurationWatcher);
    }

    /** Create a new single-node channel pool for the given service. */
    public <STUB> GrpcSingleNodeChannelPool<STUB> createSingle(ServiceKey<? extends PartitionTraits.Unicast> key,
                                                             Function<ManagedChannel, STUB> stubConstructor)
    {
        return new GrpcSingleNodeChannelPool<>(serviceRegistryIf, key, this::createChannel, stubConstructor);
    }

    private ManagedChannel createChannel(InstanceAddress route) {

        var mc = ManagedChannelBuilder
                .forAddress(route.host(), route.port())
                .executor(executor)
                .offloadExecutor(offloadExecutor)
                .usePlaintext()
                .build();

        mc.getState(true);

        return mc;
    }
}
