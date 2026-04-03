package nu.marginalia.api;

import nu.marginalia.api.domains.*;

public class DomainInfoApiMock extends DomainInfoAPIGrpc.DomainInfoAPIImplBase {

    @Override
    public void getDomainInfo(RpcDomainId request,
                              io.grpc.stub.StreamObserver<RpcDomainInfoResponse> responseObserver) {
        responseObserver.onNext(RpcDomainInfoResponse.newBuilder()
                .setDomainId(request.getDomainId())
                .setDomain("example.com")
                .setState("ACTIVE")
                .setBlacklisted(false)
                .setPingData(RpcDomainInfoPingData.newBuilder()
                        .setServerAvailable(true)
                        .setHttpSchema("HTTPS")
                        .setResponseTimeMs(120)
                        .setTsLast(1711641000000L)
                        .build())
                .setSecurityData(RpcDomainInfoSecurityData.newBuilder()
                        .setSslProtocol("TLSv1.3")
                        .setHttpVersion("HTTP/2")
                        .setHttpCompression(true)
                        .setSslCertSubject("Let's Encrypt")
                        .build())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getSimilarDomains(RpcDomainLinksRequest request,
                                  io.grpc.stub.StreamObserver<RpcSimilarDomains> responseObserver) {
        responseObserver.onNext(RpcSimilarDomains.newBuilder()
                .addDomains(RpcSimilarDomain.newBuilder()
                        .setUrl("http://similar-site.org/")
                        .setDomainId(42)
                        .setRelatedness(0.85)
                        .setRank(65.0)
                        .setLinkType(RpcSimilarDomain.LINK_TYPE.BIDIRECTIONAL)
                        .setIndexed(true)
                        .setActive(true)
                        .build())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getLinkingDomains(RpcDomainLinksRequest request,
                                  io.grpc.stub.StreamObserver<RpcSimilarDomains> responseObserver) {
        responseObserver.onNext(RpcSimilarDomains.newBuilder()
                .addDomains(RpcSimilarDomain.newBuilder()
                        .setUrl("http://linking-site.net/")
                        .setDomainId(99)
                        .setRelatedness(0.5)
                        .setRank(40.0)
                        .setLinkType(RpcSimilarDomain.LINK_TYPE.BACKWARD)
                        .setIndexed(true)
                        .setActive(false)
                        .build())
                .build());
        responseObserver.onCompleted();
    }
}
