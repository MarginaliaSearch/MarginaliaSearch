package nu.marginalia.domainranking;

import it.unimi.dsi.fastutil.ints.IntCollection;
import nu.marginalia.domaingraph.DomainGraph;

import java.util.BitSet;

/** PageRank over a {@link DomainGraph}.
 *
 * <p>If the influence set is non-empty, the random-surfer teleport
 * distribution concentrates on those vertices (Personalized PageRank).
 * Otherwise the teleport distribution is uniform, recovering standard
 * PageRank.
 *
 * <p>Adapted from the JGraphT {@code PageRank} algorithm (EPL-2.0 / LGPL-2.1-or-later).
 */
public class PersonalizedPageRank {
    public static final int MAX_ITERATIONS_DEFAULT = 100;
    public static final double TOLERANCE_DEFAULT = 0.0001;
    public static final double DAMPING_FACTOR_DEFAULT = 0.85d;

    private final DomainGraph graph;
    private final BitSet influence;
    private final int influenceSize;
    private final double dampingFactor;
    private final int maxIterations;
    private final double tolerance;

    private double[] scores;

    public PersonalizedPageRank(DomainGraph graph, IntCollection influenceVertexIds) {
        this(graph, influenceVertexIds,
                DAMPING_FACTOR_DEFAULT, MAX_ITERATIONS_DEFAULT, TOLERANCE_DEFAULT);
    }

    public PersonalizedPageRank(DomainGraph graph,
                                IntCollection influenceVertexIds,
                                double dampingFactor,
                                int maxIterations,
                                double tolerance) {
        if (maxIterations <= 0)
            throw new IllegalArgumentException("Maximum iterations must be positive");
        if (dampingFactor < 0.0 || dampingFactor > 1.0)
            throw new IllegalArgumentException("Damping factor not valid");
        if (tolerance <= 0.0)
            throw new IllegalArgumentException("Tolerance not valid, must be positive");

        this.graph = graph;
        this.dampingFactor = dampingFactor;
        this.maxIterations = maxIterations;
        this.tolerance = tolerance;

        this.influence = new BitSet(graph.size());
        int count = 0;
        if (influenceVertexIds != null) {
            for (var it = influenceVertexIds.iterator(); it.hasNext(); ) {
                int idx = graph.internalIndex(it.nextInt());
                if (idx >= 0 && !influence.get(idx)) {
                    influence.set(idx);
                    count++;
                }
            }
        }
        this.influenceSize = count;
    }

    /** Returns the score for each internal vertex index, in {@code [0, graph.size())}. */
    public double[] getScores() {
        if (scores == null) {
            scores = (influenceSize == 0) ? computeUniform() : computePersonalized();
        }
        return scores;
    }

    /** Standard Brin-Page PageRank with a uniform teleport distribution. */
    private double[] computeUniform() {
        int V = graph.size();
        double[] cur = new double[V];
        double[] next = new double[V];
        if (V == 0) return cur;

        int[] inN = graph.inNeighborsArray();
        double[] inW = graph.inWeightsArray();
        boolean weighted = graph.isWeighted();
        double init = 1d / V;
        for (int i = 0; i < V; i++) cur[i] = init;

        double[] weightSum = weighted ? buildWeightSum() : null;

        double maxChange = tolerance;
        int iterations = maxIterations;
        while (iterations > 0 && maxChange >= tolerance) {
            double dangling = 0d;
            for (int v = 0; v < V; v++) {
                if (graph.outDegree(v) == 0) dangling += cur[v];
            }
            double base = (1d - dampingFactor) / V + dampingFactor * dangling / V;

            maxChange = 0d;
            for (int v = 0; v < V; v++) {
                int from = graph.inOffset(v), to = graph.inEnd(v);
                double contribution = 0d;
                if (weighted) {
                    for (int k = from; k < to; k++) {
                        int w = inN[k];
                        double ws = weightSum[w];
                        if (ws > 0d) contribution += cur[w] * inW[k] / ws;
                    }
                } else {
                    for (int k = from; k < to; k++) {
                        int w = inN[k];
                        int od = graph.outDegree(w);
                        if (od > 0) contribution += cur[w] / od;
                    }
                }
                double newVal = base + dampingFactor * contribution;
                double diff = Math.abs(newVal - cur[v]);
                if (diff > maxChange) maxChange = diff;
                next[v] = newVal;
            }

            double[] tmp = cur; cur = next; next = tmp;
            iterations--;
        }
        return cur;
    }

    /** Personalized PageRank, with the teleport distribution concentrated on
     *  the influence set. */
    private double[] computePersonalized() {
        int V = graph.size();
        double[] cur = new double[V];
        double[] next = new double[V];
        if (V == 0) return cur;

        int[] inN = graph.inNeighborsArray();
        double[] inW = graph.inWeightsArray();
        boolean weighted = graph.isWeighted();
        double init = 1d / V;
        for (int i = 0; i < V; i++) cur[i] = init;

        double[] weightSum = weighted ? buildWeightSum() : null;

        double maxChange = tolerance;
        int iterations = maxIterations;
        while (iterations > 0 && maxChange >= tolerance) {
            double r = teleportPersonalized(cur);

            maxChange = 0d;
            for (int v = 0; v < V; v++) {
                int from = graph.inOffset(v), to = graph.inEnd(v);
                double contribution = 0d;
                if (weighted) {
                    for (int k = from; k < to; k++) {
                        int w = inN[k];
                        double ws = weightSum[w];
                        if (ws > 0d) contribution += dampingFactor * cur[w] * inW[k] / ws;
                    }
                } else {
                    for (int k = from; k < to; k++) {
                        int w = inN[k];
                        int od = graph.outDegree(w);
                        if (od > 0) contribution += dampingFactor * cur[w] / od;
                    }
                }
                double newVal = (influence.get(v) ? r : 0d) + contribution;
                double diff = Math.abs(newVal - cur[v]);
                if (diff > maxChange) maxChange = diff;
                next[v] = newVal;
            }

            double[] tmp = cur; cur = next; next = tmp;
            iterations--;
        }

        // Strip the teleport contribution from the influence set members so
        // the returned scores describe propagation alone, leaving the seed
        // vertices comparable to their neighbors.
        double r = teleportPersonalized(cur);
        for (int v = influence.nextSetBit(0); v >= 0; v = influence.nextSetBit(v + 1)) {
            cur[v] -= r;
        }
        return cur;
    }

    private double[] buildWeightSum() {
        int V = graph.size();
        int[] inN = graph.inNeighborsArray();
        double[] inW = graph.inWeightsArray();
        double[] sum = new double[V];
        for (int v = 0; v < V; v++) {
            int from = graph.inOffset(v), to = graph.inEnd(v);
            for (int k = from; k < to; k++) {
                sum[inN[k]] += inW[k];
            }
        }
        return sum;
    }

    private double teleportPersonalized(double[] cur) {
        double r = 0d;
        for (int v = influence.nextSetBit(0); v >= 0; v = influence.nextSetBit(v + 1)) {
            if (graph.outDegree(v) > 0) r += (1d - dampingFactor);
            else r += cur[v];
        }
        return r / influenceSize;
    }
}
