package nu.marginalia.asyncio;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class SubmittedReadRequestMulti<T> extends SubmittedReadRequest<T> {

    private final T context;
    private final List<AsyncReadRequest> requests;
    private final CompletableFuture<T> future;
    private int count;
    private volatile boolean success = true;

    SubmittedReadRequestMulti(T context, List<AsyncReadRequest> requests, CompletableFuture<T> future, long id) {
        super(id);
        this.context = context;
        this.requests = requests;
        this.future = future;
        this.count = requests.size();
    }

    @Override
    public List<AsyncReadRequest> getRequests() {
        return requests;
    }

    @Override
    public int count() {
        return count;
    }

    @Override
    public void canNotFinish() {
        success = false;
        count = 0;
        future.completeExceptionally(new IOException());
    }

    @Override
    public boolean partFinished(boolean successfully) {
        if (!successfully) {
            success = false;
        }

        return --count == 0;
    }

    @Override
    public void finalizeRequest() {
        if (success) {
            future.complete(context);
        } else {
            future.completeExceptionally(new IOException());
        }
    }

}