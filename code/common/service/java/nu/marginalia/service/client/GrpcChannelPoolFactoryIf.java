package nu.marginalia.service.client;

import com.google.inject.ImplementedBy;
import io.grpc.ManagedChannel;
import nu.marginalia.service.discovery.property.PartitionTraits;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;

import java.util.function.Function;

@ImplementedBy(GrpcChannelPoolFactory.class)
public interface GrpcChannelPoolFactoryIf {
    <STUB> GrpcMultiNodeChannelPool<STUB> createMulti(ServiceKey<ServicePartition.Multi> key,
                                                      Function<ManagedChannel, STUB> stubConstructor);

    <STUB> GrpcSingleNodeChannelPool<STUB> createSingle(ServiceKey<? extends PartitionTraits.Unicast> key,
                                                        Function<ManagedChannel, STUB> stubConstructor);
}
