package nu.marginalia.asyncio;

import java.util.List;

public abstract class SubmittedReadRequest<T> {
    public final long id;

    protected SubmittedReadRequest(long id) {
        this.id = id;
    }


    public abstract List<AsyncReadRequest> getRequests();

    public abstract int count();

    public abstract void canNotFinish();

    public abstract boolean partFinished(boolean successfully);

    public abstract void finalizeRequest();
}
