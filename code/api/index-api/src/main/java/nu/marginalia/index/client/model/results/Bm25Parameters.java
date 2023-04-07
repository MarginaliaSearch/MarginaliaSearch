package nu.marginalia.index.client.model.results;

/**
 * @param k  determines the size of the impact of a single term
 * @param b  determines the magnitude of the length normalization
 */
public record Bm25Parameters(double k, double b) {
}
