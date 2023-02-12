package nu.marginalia.wmsa.edge.index.model;

public record QueryLimits(int resultsByDomain, int resultsTotal, int timeoutMs, int fetchSize) {
}
