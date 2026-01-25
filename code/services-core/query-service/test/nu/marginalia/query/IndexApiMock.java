package nu.marginalia.query;

import io.grpc.stub.StreamObserver;
import nu.marginalia.api.searchquery.IndexApiGrpc;
import nu.marginalia.api.searchquery.RpcDecoratedResultItem;
import nu.marginalia.api.searchquery.RpcIndexQuery;
import nu.marginalia.api.searchquery.RpcIndexQueryResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class IndexApiMock extends IndexApiGrpc.IndexApiImplBase {
    private final List<RpcIndexQuery> queries = new ArrayList<>();

    private BiConsumer<RpcIndexQuery, StreamObserver<RpcIndexQueryResponse>> handler = null;

    public synchronized List<RpcIndexQuery> getQueries() {
        return new ArrayList<>(queries);
    }
    public synchronized void reset() {
        queries.clear();
        handler = null;
    }

    public synchronized void setHandler(BiConsumer<RpcIndexQuery, StreamObserver<RpcIndexQueryResponse>> handler) {
        this.handler = handler;
    }

    @Override
    public synchronized void query(RpcIndexQuery request, StreamObserver<nu.marginalia.api.searchquery.RpcIndexQueryResponse> responseObserver) {
        System.out.println(request);
        queries.add(request);
        if (handler != null) {
            handler.accept(request, responseObserver);
        } else {
            responseObserver.onCompleted();
        }
    }
}
