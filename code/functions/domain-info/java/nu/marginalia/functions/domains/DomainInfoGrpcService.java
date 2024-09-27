package nu.marginalia.functions.domains;

import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;
import nu.marginalia.api.domains.*;
import nu.marginalia.service.server.DiscoverableService;

public class DomainInfoGrpcService
        extends DomainInfoAPIGrpc.DomainInfoAPIImplBase
        implements DiscoverableService
{

    private final DomainInformationService domainInformationService;
    private final SimilarDomainsService similarDomainsService;
    @Inject
    public DomainInfoGrpcService(DomainInformationService domainInformationService, SimilarDomainsService similarDomainsService)
    {

        this.domainInformationService = domainInformationService;
        this.similarDomainsService = similarDomainsService;
    }

    @Override
    public void getDomainInfo(RpcDomainId request, StreamObserver<RpcDomainInfoResponse> responseObserver) {
        var ret = domainInformationService.domainInfo(request.getDomainId());

        ret.ifPresent(responseObserver::onNext);

        responseObserver.onCompleted();
    }

    @Override
    public void getSimilarDomains(RpcDomainLinksRequest request,
                                  StreamObserver<RpcSimilarDomains> responseObserver) {


        var responseBuilder = RpcSimilarDomains.newBuilder();

        if (similarDomainsService.isReady()) {
            var ret = similarDomainsService.getSimilarDomains(request.getDomainId(), request.getCount());
            responseBuilder.addAllDomains(ret);
        }


        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getLinkingDomains(RpcDomainLinksRequest request, StreamObserver<RpcSimilarDomains> responseObserver) {
        var responseBuilder = RpcSimilarDomains.newBuilder();

        if (similarDomainsService.isReady()) {
            var ret = similarDomainsService.getLinkingDomains(request.getDomainId(), request.getCount());
            responseBuilder.addAllDomains(ret);
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }
}
