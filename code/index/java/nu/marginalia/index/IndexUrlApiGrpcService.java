package nu.marginalia.index;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import nu.marginalia.api.searchquery.IndexUrlApiGrpc;
import nu.marginalia.api.searchquery.RpcDocumentIdRequest;
import nu.marginalia.api.searchquery.RpcIndexUrlResponse;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.service.server.DiscoverableService;

import java.sql.SQLException;

@Singleton
public class IndexUrlApiGrpcService extends IndexUrlApiGrpc.IndexUrlApiImplBase
        implements DiscoverableService
{

    private final DocumentDbReader documentDbReader;

    @Inject
    public IndexUrlApiGrpcService(DocumentDbReader documentDbReader) {
        this.documentDbReader = documentDbReader;
    }

    @Override
    public void getUrl(RpcDocumentIdRequest request,
                       StreamObserver<RpcIndexUrlResponse> responseObserver)
    {
        try {
            String url = documentDbReader.getUrl(request.getDocId());

            if (url != null) {
                responseObserver.onNext(RpcIndexUrlResponse.newBuilder().setUrl(url).build());
                responseObserver.onCompleted();
            }
            else {
                // Don't return a 404, just don't return a blank URL so we don't drag a stack trace across for every bad URL
                responseObserver.onNext(RpcIndexUrlResponse.newBuilder().build());
                responseObserver.onCompleted();
                return;
            }
        }
        catch (IllegalStateException ex) {
            responseObserver.onError(Status.UNAVAILABLE.withCause(ex).asRuntimeException());
        }
        catch (SQLException | RuntimeException ex) {
            responseObserver.onError(Status.INTERNAL.withCause(ex).asRuntimeException());
        }
    }
}
