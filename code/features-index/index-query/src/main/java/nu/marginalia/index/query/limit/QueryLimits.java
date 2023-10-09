package nu.marginalia.index.query.limit;

public record QueryLimits(int resultsByDomain, int resultsTotal, int timeoutMs, int fetchSize) {
    public QueryLimits forSingleDomain() {
        return new QueryLimits(resultsTotal, resultsTotal, timeoutMs, fetchSize);
    }
}
