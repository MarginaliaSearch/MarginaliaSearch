package nu.marginalia.api;

import nu.marginalia.api.searchquery.*;

import java.util.ArrayList;
import java.util.List;

public class QueryApiMock extends QueryApiGrpc.QueryApiImplBase{
    private final List<RpcQsQuery> sentQueries = new ArrayList<>();
    private final List<RpcQsInvalidateFilter> sentInvalidationRequests = new ArrayList<>();

    public synchronized List<RpcQsQuery> getSentQueries() {
        return new ArrayList<>(sentQueries);
    }

    public synchronized List<RpcQsInvalidateFilter> getSetInvalidations() {
        return new ArrayList<>(sentInvalidationRequests);
    }

    public synchronized void reset() {
        sentQueries.clear();
        sentInvalidationRequests.clear();
    }

    @Override
    public synchronized void query(nu.marginalia.api.searchquery.RpcQsQuery request,
                      io.grpc.stub.StreamObserver<nu.marginalia.api.searchquery.RpcQsResponse> responseObserver) {
        System.out.println(request);
        sentQueries.add(request);
        responseObserver.onNext(RpcQsResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    public synchronized  void invalidateFilterCache(nu.marginalia.api.searchquery.RpcQsInvalidateFilter request,
                                      io.grpc.stub.StreamObserver<nu.marginalia.api.searchquery.Empty> responseObserver) {
        System.out.println(request);
        sentInvalidationRequests.add(request);
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
