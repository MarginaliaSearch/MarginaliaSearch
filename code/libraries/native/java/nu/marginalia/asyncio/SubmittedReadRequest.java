package nu.marginalia.asyncio;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class SubmittedReadRequest<T> {

    public  final long id;

    private final T context;
    private final List<AsyncReadRequest> requests;
    private final CompletableFuture<T> future;
    private int count;
    private volatile boolean success = true;

    SubmittedReadRequest(T context, List<AsyncReadRequest> requests, CompletableFuture<T> future, long id) {
        this.context = context;
        this.requests = requests;
        this.future = future;
        this.id = id;
        this.count = requests.size();
    }

    public List<AsyncReadRequest> getRequests() {
        return requests;
    }

    public int count() {
        return count;
    }

    public void canNotFinish() {
        success = false;
        count = 0;
        future.completeExceptionally(new IOException());
    }

    public boolean partFinished(boolean successfully) {
        if (!successfully) {
            success = false;
        }

        if (--count == 0) {
            if (success) {
                future.complete(context);
            } else {
                future.completeExceptionally(new IOException());
            }
            return true;
        }
        return false;
    }

}