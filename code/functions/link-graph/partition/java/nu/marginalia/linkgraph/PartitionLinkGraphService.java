package nu.marginalia.linkgraph;

import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;
import nu.marginalia.api.linkgraph.*;
import nu.marginalia.api.linkgraph.Empty;
import nu.marginalia.api.linkgraph.LinkGraphApiGrpc;

/**  GRPC service for interrogating domain links for a single partition.  For accessing the data
 * in the application, the AggregateLinkGraphService should be used instead via the
 * AggregateLinkGraphClient.
 */
public class PartitionLinkGraphService extends LinkGraphApiGrpc.LinkGraphApiImplBase {
    private final DomainLinks domainLinks;

    @Inject
    public PartitionLinkGraphService(DomainLinks domainLinks) {
        this.domainLinks = domainLinks;
    }

    public void getAllLinks(Empty request,
                            io.grpc.stub.StreamObserver<RpcDomainIdPairs> responseObserver) {

        try (var idsConverter = new AllIdsResponseConverter(responseObserver)) {
            domainLinks.forEach(idsConverter::accept);
        }

        responseObserver.onCompleted();
    }

    private static class AllIdsResponseConverter implements AutoCloseable {
        private RpcDomainIdPairs.Builder builder;
        private final io.grpc.stub.StreamObserver<RpcDomainIdPairs> responseObserver;
        private int n = 0;

        private AllIdsResponseConverter(io.grpc.stub.StreamObserver<RpcDomainIdPairs> responseObserver) {
            this.responseObserver = responseObserver;
            this.builder = RpcDomainIdPairs.newBuilder();
        }

        public void accept(int source, int dest) {
            builder.addSourceIds(source);
            builder.addDestIds(dest);

            if (++n > 1000) {
                responseObserver.onNext(builder.build());
                builder = RpcDomainIdPairs.newBuilder();
                n = 0;
            }
        }

        @Override
        public void close() {
            if (n > 0) {
                responseObserver.onNext(builder.build());
            }
        }
    }

    @Override
    public void getLinksFromDomain(RpcDomainId request,
                                   StreamObserver<RpcDomainIdList> responseObserver) {

        var links = domainLinks.findDestinations(request.getDomainId());

        var rspBuilder = RpcDomainIdList.newBuilder();
        for (int i = 0; i < links.size(); i++) {
            rspBuilder.addDomainId(links.get(i));
        }
        responseObserver.onNext(rspBuilder.build());

        responseObserver.onCompleted();
    }

    @Override
    public void getLinksToDomain(RpcDomainId request,
                                 StreamObserver<RpcDomainIdList> responseObserver) {

        var links = domainLinks.findSources(request.getDomainId());

        var rspBuilder = RpcDomainIdList.newBuilder();
        for (int i = 0; i < links.size(); i++) {
            rspBuilder.addDomainId(links.get(i));
        }
        responseObserver.onNext(rspBuilder.build());

        responseObserver.onCompleted();
    }

    public void countLinksFromDomain(RpcDomainId request,
                                     StreamObserver<RpcDomainIdCount> responseObserver) {
        responseObserver.onNext(RpcDomainIdCount.newBuilder()
                .setIdCount(domainLinks.countDestinations(request.getDomainId()))
                .build());
        responseObserver.onCompleted();
    }

    public void countLinksToDomain(RpcDomainId request,
                                   StreamObserver<RpcDomainIdCount> responseObserver) {
        responseObserver.onNext(RpcDomainIdCount.newBuilder()
                .setIdCount(domainLinks.countSources(request.getDomainId()))
                .build());
        responseObserver.onCompleted();
    }
}
