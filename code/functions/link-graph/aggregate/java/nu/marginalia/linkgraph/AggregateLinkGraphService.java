package nu.marginalia.linkgraph;

import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;
import nu.marginalia.api.linkgraph.*;
import nu.marginalia.api.linkgraph.LinkGraphApiGrpc.LinkGraphApiBlockingStub;
import nu.marginalia.service.server.DiscoverableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** This class is responsible for aggregating the link graph data from the partitioned link graph
 * services.
 */
public class AggregateLinkGraphService
        extends LinkGraphApiGrpc.LinkGraphApiImplBase
        implements DiscoverableService
{
    private static final Logger logger = LoggerFactory.getLogger(AggregateLinkGraphService.class);
    private final PartitionLinkGraphClient client;

    @Inject
    public AggregateLinkGraphService(PartitionLinkGraphClient client) {
        this.client = client;
    }

    @Override
    public void getAllLinks(Empty request,
                            StreamObserver<RpcDomainIdPairs> responseObserver) {

        client.getChannelPool().call(LinkGraphApiBlockingStub::getAllLinks)
                .run(Empty.getDefaultInstance())
                .forEach(iter -> iter.forEachRemaining(responseObserver::onNext));

        responseObserver.onCompleted();
    }

    @Override
    public void getLinksFromDomain(RpcDomainId request,
                                   StreamObserver<RpcDomainIdList> responseObserver) {
        var rspBuilder = RpcDomainIdList.newBuilder();

        client.getChannelPool().call(LinkGraphApiBlockingStub::getLinksFromDomain)
                .run(request)
                .stream()
                .map(RpcDomainIdList::getDomainIdList)
                .flatMap(List::stream)
                .forEach(rspBuilder::addDomainId);

        responseObserver.onNext(rspBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getLinksToDomain(RpcDomainId request,
                                 StreamObserver<RpcDomainIdList> responseObserver) {
        var rspBuilder = RpcDomainIdList.newBuilder();


        client.getChannelPool().call(LinkGraphApiBlockingStub::getLinksToDomain)
                .run(request)
                .stream()
                .map(RpcDomainIdList::getDomainIdList)
                .flatMap(List::stream)
                .forEach(rspBuilder::addDomainId);

        responseObserver.onNext(rspBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void countLinksFromDomain(RpcDomainId request,
                                     StreamObserver<RpcDomainIdCount> responseObserver) {
        int sum = client.getChannelPool().call(LinkGraphApiBlockingStub::countLinksFromDomain)
                .run(request)
                .stream()
                .mapToInt(RpcDomainIdCount::getIdCount)
                .sum();

        var rspBuilder = RpcDomainIdCount.newBuilder();
        rspBuilder.setIdCount(sum);
        responseObserver.onNext(rspBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void countLinksToDomain(RpcDomainId request,
                                   StreamObserver<RpcDomainIdCount> responseObserver) {

        int sum = client.getChannelPool().call(LinkGraphApiBlockingStub::countLinksToDomain)
                .run(request)
                .stream()
                .mapToInt(RpcDomainIdCount::getIdCount)
                .sum();

        var rspBuilder = RpcDomainIdCount.newBuilder();
        rspBuilder.setIdCount(sum);
        responseObserver.onNext(rspBuilder.build());
        responseObserver.onCompleted();
    }

}
