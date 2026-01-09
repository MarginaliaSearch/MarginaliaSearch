package nu.marginalia.buffering;

import java.util.concurrent.atomic.AtomicReference;

public class SingBuffer<T> {
    private final AtomicReference<T> value = new AtomicReference<T>();

    public SingBuffer() {}

    @SuppressWarnings("unchecked")
    public T take() {
        var ret = value.getAcquire();
        if (ret != null && value.weakCompareAndSetPlain(ret, null)) {
            return ret;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public boolean put(T val) {
        return value.weakCompareAndSetRelease(null, val);
    }

    volatile boolean closed = false;

    public void close() {
        closed = true;
    }
    public boolean isClosed() {
        return closed;
    }
    public void reset() {
        value.set(null);
        closed = false;
    }
}
