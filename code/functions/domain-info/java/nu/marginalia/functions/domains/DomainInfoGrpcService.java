package nu.marginalia.functions.domains;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import nu.marginalia.api.domains.*;
import nu.marginalia.service.server.DiscoverableService;

import java.time.Duration;

public class DomainInfoGrpcService
        extends DomainInfoAPIGrpc.DomainInfoAPIImplBase
        implements DiscoverableService
{
    private final DomainInformationService domainInformationService;
    private final SimilarDomainsService similarDomainsService;

    private final LoadingCache<RpcDomainId, RpcDomainInfoResponse> domainInfoCache;

    @Inject
    public DomainInfoGrpcService(DomainInformationService domainInformationService, SimilarDomainsService similarDomainsService)
    {
        this.domainInformationService = domainInformationService;
        this.similarDomainsService = similarDomainsService;

        this.domainInfoCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .refreshAfterWrite(Duration.ofHours(1))
                .build(new CacheLoader<RpcDomainId, RpcDomainInfoResponse>() {
                    @Override
                    public RpcDomainInfoResponse load(RpcDomainId key) throws Exception {
                        return domainInformationService.domainInfo(key.getDomainId()).orElseThrow();
                    }
                });
    }

    @Override
    public void getDomainInfo(RpcDomainId request, StreamObserver<RpcDomainInfoResponse> responseObserver) {
        var ret = domainInformationService.domainInfo(request.getDomainId());

        try {
            responseObserver.onNext(domainInfoCache.get(request));
        }
        catch (Exception ex) {
            responseObserver.onError(Status.INTERNAL.asRuntimeException());
        }

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
