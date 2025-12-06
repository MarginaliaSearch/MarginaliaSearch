package nu.marginalia.service.client;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.ManagedChannel;
import nu.marginalia.service.NodeConfigurationWatcherIf;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.monitor.ServiceMonitorIf;
import nu.marginalia.service.discovery.property.PartitionTraits;
import nu.marginalia.service.discovery.property.ServiceEndpoint;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreV2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class TestGrpcChannelPoolFactory implements GrpcChannelPoolFactoryIf, ServiceRegistryIf, NodeConfigurationWatcherIf {
    String serviceName;
    Server inProcessServer;
    List<ManagedChannel> channels = new ArrayList<>();

    public TestGrpcChannelPoolFactory(List<BindableService> serviceList) throws IOException {
        serviceName = InProcessServerBuilder.generateName();

        var serviceBuilder = InProcessServerBuilder.forName(serviceName)
                .directExecutor();
        serviceList.forEach(serviceBuilder::addService);

        inProcessServer = serviceBuilder.build();
        inProcessServer.start();
    }

    public void close() {
        channels.forEach(ManagedChannel::shutdownNow);
        inProcessServer.shutdownNow();
    }

    /** Create a new multi-node channel pool for the given service. */
    @Override
    public <STUB> GrpcMultiNodeChannelPool<STUB> createMulti(ServiceKey<ServicePartition.Multi> key,
                                                             Function<ManagedChannel, STUB> stubConstructor)
    {
        return new GrpcMultiNodeChannelPool<>(this,
                key == null ? new ServiceKey.Grpc<>("test", new ServicePartition.Multi()) : key,
                this::createChannel,
                stubConstructor,
                this);
    }

    private ManagedChannel createChannel(ServiceEndpoint.InstanceAddress instanceAddress) {
        ManagedChannel channel = InProcessChannelBuilder.forName(serviceName)
                .directExecutor()
                .build();
        channels.add(channel);
        return channel;
    }

    /** Create a new single-node channel pool for the given service. */
    @Override
    public <STUB> GrpcSingleNodeChannelPool<STUB> createSingle(ServiceKey<? extends PartitionTraits.Unicast> key,
                                                               Function<ManagedChannel, STUB> stubConstructor)
    {
        try {
            return new GrpcSingleNodeChannelPool<>(this,
                    key == null ? new ServiceKey.Grpc<>("test", new ServicePartition.Any()) : key,
                    this::createChannel,
                    stubConstructor);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ServiceEndpoint registerService(ServiceKey<?> key, UUID instanceUUID, String externalAddress) throws Exception {
        return new ServiceEndpoint("", 0);
    }

    @Override
    public void declareFirstBoot() {}

    @Override
    public void waitForFirstBoot() throws InterruptedException {}

    @Override
    public void announceInstance(UUID instanceUUID) {}

    @Override
    public int requestPort(String externalHost, ServiceKey<?> key) {return 0;}

    @Override
    public List<ServiceEndpoint.InstanceAddress> getEndpoints(ServiceKey<?> schema) {
        return List.of(new ServiceEndpoint.InstanceAddress(new ServiceEndpoint("test", 0), UUID.randomUUID()));
    }

    @Override
    public void registerMonitor(ServiceMonitorIf monitor) throws Exception {}

    @Override
    public void registerProcess(String processName, int nodeId) {}

    @Override
    public void deregisterProcess(String processName, int nodeId) {}

    @Override
    public InterProcessSemaphoreV2 getSemaphore(String name, int permits) throws Exception {return null;}

    @Override
    public List<Integer> getQueryNodes() {
        return List.of(1);
    }
}
