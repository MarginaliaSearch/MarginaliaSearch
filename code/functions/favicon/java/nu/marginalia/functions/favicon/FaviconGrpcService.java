package nu.marginalia.functions.favicon;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import nu.marginalia.api.favicon.FaviconAPIGrpc;
import nu.marginalia.api.favicon.RpcFaviconRequest;
import nu.marginalia.api.favicon.RpcFaviconResponse;
import nu.marginalia.crawl.DomainStateDb;
import nu.marginalia.service.server.DiscoverableService;

import java.util.Optional;

@Singleton
public class FaviconGrpcService extends FaviconAPIGrpc.FaviconAPIImplBase implements DiscoverableService {
    private final DomainStateDb domainStateDb;

    @Inject
    public FaviconGrpcService(DomainStateDb domainStateDb) {
        this.domainStateDb = domainStateDb;
    }

    @Override
    public void getFavicon(RpcFaviconRequest request, StreamObserver<RpcFaviconResponse> responseObserver) {
        Optional<DomainStateDb.FaviconRecord> icon = domainStateDb.getIcon(request.getDomain());

        RpcFaviconResponse response;
        if (icon.isEmpty()) {
            response = RpcFaviconResponse.newBuilder().build();
        }
        else {
            var iconRecord = icon.get();
            response = RpcFaviconResponse.newBuilder()
                            .setContentType(iconRecord.contentType())
                            .setDomain(request.getDomain())
                            .setData(ByteString.copyFrom(iconRecord.imageData()))
                            .build();
        }

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
