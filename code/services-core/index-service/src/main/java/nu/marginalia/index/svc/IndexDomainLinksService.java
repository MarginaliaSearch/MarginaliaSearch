package nu.marginalia.index.svc;

import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;
import nu.marginalia.index.api.*;
import nu.marginalia.linkdb.dlinks.DomainLinkDb;

/**  GRPC service for interrogating domain links
 */
public class IndexDomainLinksService extends IndexDomainLinksApiGrpc.IndexDomainLinksApiImplBase {
    private final DomainLinkDb domainLinkDb;

    @Inject
    public IndexDomainLinksService(DomainLinkDb domainLinkDb) {
        this.domainLinkDb = domainLinkDb;
    }

    public void getAllLinks(nu.marginalia.index.api.Empty request,
                            io.grpc.stub.StreamObserver<nu.marginalia.index.api.RpcDomainIdPairs> responseObserver) {

        try (var idsConverter = new AllIdsResponseConverter(responseObserver)) {
            domainLinkDb.forEach(idsConverter::accept);
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

        var links = domainLinkDb.findDestinations(request.getDomainId());

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

        var links = domainLinkDb.findSources(request.getDomainId());

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
                .setIdCount(domainLinkDb.countDestinations(request.getDomainId()))
                .build());
        responseObserver.onCompleted();
    }

    public void countLinksToDomain(RpcDomainId request,
                                   StreamObserver<RpcDomainIdCount> responseObserver) {
        responseObserver.onNext(RpcDomainIdCount.newBuilder()
                .setIdCount(domainLinkDb.countSources(request.getDomainId()))
                .build());
        responseObserver.onCompleted();
    }
}
