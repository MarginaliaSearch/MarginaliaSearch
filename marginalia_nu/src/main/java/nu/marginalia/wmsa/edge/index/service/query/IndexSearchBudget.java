package nu.marginalia.wmsa.edge.index.service.query;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
public class IndexSearchBudget {
    private final long limit;
    private long used = 0;

    public boolean take(long unused) {
        return used++ < limit;
    }

    public long used() {
        return used;
    }
    public long limit() { return limit; }
}
