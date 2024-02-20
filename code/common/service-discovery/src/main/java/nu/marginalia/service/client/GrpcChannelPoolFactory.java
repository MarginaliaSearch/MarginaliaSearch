package nu.marginalia.service.client;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import nu.marginalia.service.NodeConfigurationWatcher;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.property.ServiceEndpoint.InstanceAddress;
import nu.marginalia.service.id.ServiceId;

import java.util.function.Function;

@Singleton
public class GrpcChannelPoolFactory {

    private final NodeConfigurationWatcher nodeConfigurationWatcher;
    private final ServiceRegistryIf serviceRegistryIf;

    @Inject
    public GrpcChannelPoolFactory(NodeConfigurationWatcher nodeConfigurationWatcher,
                                  ServiceRegistryIf serviceRegistryIf)
    {
        this.nodeConfigurationWatcher = nodeConfigurationWatcher;
        this.serviceRegistryIf = serviceRegistryIf;
    }

    /** Create a new multi-node channel pool for the given service. */
    public <STUB> GrpcMultiNodeChannelPool<STUB> createMulti(ServiceId serviceId,
                                                             Function<ManagedChannel, STUB> stubConstructor)
    {
        return new GrpcMultiNodeChannelPool<>(serviceRegistryIf,
                serviceId,
                this::createChannel,
                stubConstructor,
                nodeConfigurationWatcher);
    }

    /** Create a new single-node channel pool for the given service. */
    public <STUB> GrpcSingleNodeChannelPool<STUB> createSingle(ServiceId serviceId,
                                                             Function<ManagedChannel, STUB> stubConstructor)
    {
        return new GrpcSingleNodeChannelPool<>(serviceRegistryIf, serviceId,
                new NodeSelectionStrategy.Any(),
                this::createChannel,
                stubConstructor);
    }

    private ManagedChannel createChannel(InstanceAddress<?> route) {
        return ManagedChannelBuilder
                .forAddress(route.host(), route.port())
                .usePlaintext()
                .build();
    }
}
