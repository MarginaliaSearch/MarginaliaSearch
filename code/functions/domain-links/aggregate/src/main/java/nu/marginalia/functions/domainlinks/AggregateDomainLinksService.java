package nu.marginalia.functions.domainlinks;

import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;
import nu.marginalia.api.domainlink.*;
import nu.marginalia.api.indexdomainlinks.PartitionDomainLinksClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class AggregateDomainLinksService extends DomainLinksApiGrpc.DomainLinksApiImplBase {
    private static final Logger logger = LoggerFactory.getLogger(AggregateDomainLinksService.class);
    private final PartitionDomainLinksClient client;

    @Inject
    public AggregateDomainLinksService(PartitionDomainLinksClient client) {
        this.client = client;
    }

    @Override
    public void getAllLinks(Empty request,
                            StreamObserver<RpcDomainIdPairs> responseObserver) {

        client.getChannelPool().call(DomainLinksApiGrpc.DomainLinksApiBlockingStub::getAllLinks)
                .run(Empty.getDefaultInstance())
                .forEach(iter -> iter.forEachRemaining(responseObserver::onNext));

        responseObserver.onCompleted();
    }

    @Override
    public void getLinksFromDomain(RpcDomainId request,
                                   StreamObserver<RpcDomainIdList> responseObserver) {
        var rspBuilder = RpcDomainIdList.newBuilder();

        client.getChannelPool().call(DomainLinksApiGrpc.DomainLinksApiBlockingStub::getLinksFromDomain)
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


        client.getChannelPool().call(DomainLinksApiGrpc.DomainLinksApiBlockingStub::getLinksToDomain)
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
        int sum = client.getChannelPool().call(DomainLinksApiGrpc.DomainLinksApiBlockingStub::countLinksFromDomain)
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

        int sum = client.getChannelPool().call(DomainLinksApiGrpc.DomainLinksApiBlockingStub::countLinksToDomain)
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
