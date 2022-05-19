package nu.marginalia.wmsa.edge.index.service.query;

import lombok.RequiredArgsConstructor;

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
