package nu.marginalia.index.client.model.results;

/** Tuning parameters for BM25.
 *
 * @param k  determines the size of the impact of a single term
 * @param b  determines the magnitude of the length normalization
 *
 * @see nu.marginalia.ranking.factors.Bm25Factor
 */
public record Bm25Parameters(double k, double b) {
}
