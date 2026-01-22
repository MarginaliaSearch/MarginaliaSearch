package nu.marginalia.asyncio;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class SubmittedReadRequestSingle<T> extends SubmittedReadRequest<T> {

    private final T context;
    private final AsyncReadRequest request;
    private final CompletableFuture<T> future;
    private volatile boolean success = true;

    SubmittedReadRequestSingle(T context, AsyncReadRequest request, CompletableFuture<T> future, long id) {
        super(id);

        this.context = context;
        this.request = request;
        this.future = future;
    }

    @Override
    public List<AsyncReadRequest> getRequests() {
        return List.of(request);
    }

    @Override
    public int count() {
        return 1;
    }

    @Override
    public void canNotFinish() {
        success = false;
    }

    @Override
    public boolean partFinished(boolean successfully) {
        success = successfully;
        return true;
    }

    public void finalizeRequest() {
        if (success) {
            future.complete(context);
        }
        else {
            future.completeExceptionally(new IOException());
        }
    }

}