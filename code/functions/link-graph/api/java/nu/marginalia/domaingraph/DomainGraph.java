package nu.marginalia.domaingraph;

import java.util.Arrays;
import java.util.EnumSet;

public final class DomainGraph {
    private final EnumSet<GraphTrait> traits;

    private final int[] vertexIds;
    private final int[] inOffsets;
    private final int[] outOffsets;
    private final int[] inNeighbors;
    private final int[] outNeighbors;
    private final double[] inWeights;
    private final double[] outWeights;

    public DomainGraph(EnumSet<GraphTrait> traits,
                       int[] vertexIds,
                       int[] inOffsets,
                       int[] inNeighbors,
                       double[] inWeights,
                       int[] outOffsets,
                       int[] outNeighbors,
                       double[] outWeights)
    {
        this.traits = traits;
        this.vertexIds = vertexIds;

        this.inOffsets = inOffsets;
        this.inNeighbors = inNeighbors;
        this.inWeights = inWeights;

        this.outOffsets = outOffsets;
        this.outNeighbors = outNeighbors;
        this.outWeights = outWeights;

        if (traits.contains(GraphTrait.WEIGHTED)) {
            if (inWeights == null || outWeights == null) {
                throw new IllegalArgumentException("Graph is weighted, but weights are missing");
            }
        } else {
            if (inWeights != null || outWeights != null) {
                throw new IllegalArgumentException("Graph is unweighted, but weights are present");
            }
        }
    }

    /** Return true if the two graphs have the same internal vertex id mapping. */
    public boolean isIdCompatible(DomainGraph other) {
        return Arrays.equals(vertexIds, other.vertexIds);
    }

    public int size() {
        return vertexIds.length;
    }

    public boolean isWeighted() {
        return traits.contains(GraphTrait.WEIGHTED);
    }

    public boolean isDirected() {
        return traits.contains(GraphTrait.DIRECTED);
    }

    public int vertexId(int internalIdx) {
        return vertexIds[internalIdx];
    }

    public int inDegree(int internalIdx) {
        return inOffsets[internalIdx + 1] - inOffsets[internalIdx];
    }
    public int outDegree(int internalIdx) {
        return outOffsets[internalIdx + 1] - outOffsets[internalIdx];
    }

    public int inOffset(int internalIdx) {
        return inOffsets[internalIdx];
    }

    public int inEnd(int internalIdx) {
        return inOffsets[internalIdx + 1];
    }

    public int outOffset(int internalIdx) {
        return outOffsets[internalIdx];
    }

    public int outEnd(int internalIdx) {
        return outOffsets[internalIdx + 1];
    }

    public int[] inNeighborsArray() {
        return inNeighbors;
    }

    public int[] outNeighborsArray() {
        return outNeighbors;
    }

    public double[] inWeightsArray() {
        return inWeights;
    }

    public double[] outWeightsArray() {
        return outWeights;
    }

    /** Returns the internal index of the given external vertex id, or negative if not found in graph. */
    public int internalIndex(int vertexId) {
        return Arrays.binarySearch(vertexIds, vertexId);
    }

    public boolean containsVertex(int vertexId) {
        return internalIndex(vertexId) >= 0;
    }

    public boolean containsEdge(int source, int dest) {
        int srcIdx = internalIndex(source);
        if (srcIdx < 0)
            return false;

        int dstIdx = internalIndex(dest);
        if (dstIdx < 0)
            return false;

        if (traits.contains(GraphTrait.SORTED)) {
            return Arrays.binarySearch(inNeighbors, inOffsets[dstIdx], inOffsets[dstIdx + 1], srcIdx) >= 0;
        } else {
            for (int k = inOffsets[dstIdx]; k < inOffsets[dstIdx + 1]; k++) {
                if (inNeighbors[k] == srcIdx) return true;
            }
        }

        return false;
    }

    public EdgeRange inEdges(int internalIdx) {
        return new EdgeRange(inNeighbors, inOffsets[internalIdx], inOffsets[internalIdx + 1]);
    }

    public EdgeBloomFilter inEdgesBloomFilter(int sizeLongs) {
        var filter = new EdgeBloomFilter(vertexIds.length, sizeLongs);

        for (int i = 0; i < vertexIds.length; i++) {
            filter.populateRange(i, inNeighbors, inOffsets[i], inOffsets[i + 1]);
        }
        return filter;
    }

    public EdgeRange outEdges(int internalIdx) {
        return new EdgeRange(outNeighbors, outOffsets[internalIdx], outOffsets[internalIdx + 1]);
    }


    public EdgeBloomFilter outEdgesBloomFilter(int sizeLongs) {
        var filter = new EdgeBloomFilter(vertexIds.length, sizeLongs);

        for (int i = 0; i < vertexIds.length; i++) {
            filter.populateRange(i, outNeighbors, outOffsets[i], outOffsets[i + 1]);
        }
        return filter;
    }

    public OverlapRange inOverlapEdges(int internalIdxA, int internalIdxB) {
        if (!traits.contains(GraphTrait.SORTED)) {
            throw new IllegalStateException("Graph is not sorted, cannot compute overlap edges");
        }

        return new OverlapRange(inNeighbors,
                inOffsets[internalIdxA],
                inOffsets[internalIdxA + 1],
                inOffsets[internalIdxB],
                inOffsets[internalIdxB + 1]);
    }

    public OverlapRange outOverlapEdges(int internalIdxA, int internalIdxB) {
        if (!traits.contains(GraphTrait.SORTED)) {
            throw new IllegalStateException("Graph is not sorted, cannot compute overlap edges");
        }

        return new OverlapRange(outNeighbors,
                outOffsets[internalIdxA],
                outOffsets[internalIdxA + 1],
                outOffsets[internalIdxB],
                outOffsets[internalIdxB + 1]);
    }

    /** Returns the weight of the edge from {@code source} to {@code dest},
     *  or NaN if either endpoint is unknown or the graph is
     *  unweighted. */
    public double inEdgeWeight(int source, int dest) {
        if (!traits.contains(GraphTrait.WEIGHTED)) return Double.NaN;

        int srcIdx = internalIndex(source);
        if (srcIdx < 0)
            return Double.NaN;

        int dstIdx = internalIndex(dest);
        if (dstIdx < 0)
            return Double.NaN;

        // can not be sorted
        for (int k = inOffsets[dstIdx]; k < inOffsets[dstIdx + 1]; k++) {
            if (inNeighbors[k] == srcIdx) return inWeights[k];
        }

        return Double.NaN;
    }


    public static class EdgeRange {
        private final int start;
        private final int end;
        private final int[] neighbors;

        private int pos;

        public EdgeRange(int[] neighbors, int start, int end) {
            if (start < 0 || (start >= neighbors.length && end > start)) throw new IllegalArgumentException("Invalid start: " + start);
            if (end < 0 || end > neighbors.length) throw new IllegalArgumentException("Invalid end: " + end);
            if (start > end) throw new IllegalArgumentException("Invalid range: " + start + " to " + end);

            this.neighbors = neighbors;
            this.start = start;
            this.end = end;
            this.pos = start;
        }

        public boolean hasNext() {
            return pos < end;
        }

        public int nextInternalId() {
            return neighbors[pos++];
        }

        public int size() {
            return end - start;
        }
    }

    public static class OverlapRange {
        private final int[] neighbors;

        private final int startA;
        private final int endA;
        private final int startB;
        private final int endB;

        private int posA;
        private int posB;

        private int next;

        private static final int IMBALANCED_FACTOR = 8;
        private static final int GALLOP_THRESHOLD = 64;

        public OverlapRange(int[] neighbors, int startA, int endA, int startB, int endB) {
            this.neighbors = neighbors;

            if (startA < 0 || (endA > startA && startA >= neighbors.length)) throw new IllegalArgumentException("Invalid startA: " + startA);
            if (endA < 0 || endA > neighbors.length) throw new IllegalArgumentException("Invalid endA: " + endA);
            if (startB < 0 || (endB > startB && startB >= neighbors.length)) throw new IllegalArgumentException("Invalid startB: " + startB);
            if (endB < 0 || endB > neighbors.length) throw new IllegalArgumentException("Invalid endB: " + endB);

            this.startA = startA;
            this.endA = endA;
            this.startB = startB;
            this.endB = endB;

            this.posA = startA;
            this.posB = startB;

            next = Integer.MIN_VALUE;
        }

        public boolean findNext() {
            int remA = endA - posA;
            int remB = endB - posB;

            // Handle edge cases when checking a small number of needles in a large haystack
            if (IMBALANCED_FACTOR * remA < remB) {
                return findNextImbalancedB();
            }
            else if (IMBALANCED_FACTOR * remB < remA) {
                return findNextImbalancedA();
            }

            // Galloping phase
            while (remA > 0 && remB > 0 && (remA > GALLOP_THRESHOLD || remB > GALLOP_THRESHOLD))
            {
                int valA = neighbors[posA];
                int valB = neighbors[posB];

                if (valA == valB) {
                    next = valA;
                    posA++;
                    posB++;
                    return true;
                }

                if (valA < valB) {
                    posA = Arrays.binarySearch(neighbors, posA, endA, valB);

                    if (posA >= 0) {
                        posA++;
                        posB++;
                        next = valB;
                        return true;
                    }

                    posA = -posA - 1;
                    remA = endA - posA;
                } else {
                    posB = Arrays.binarySearch(neighbors, posB, endB, valA);

                    if (posB >= 0) {
                        posA++;
                        posB++;
                        next = valA;
                        return true;
                    }

                    posB = -posB - 1;
                    remB = endB - posB;
                }
            }

            // Linear phase
            while (remA > 0 && remB > 0) {
                int valA = neighbors[posA];
                int valB = neighbors[posB];

                if (valA == valB) {
                    next = valA;
                    posA++;
                    posB++;
                    return true;
                }

                if (valA < valB) {
                    posA++;
                    remA--;
                } else {
                    posB++;
                    remB--;
                }
            }

            return false;
        }

        public boolean findNextImbalancedA() {
            while (posA < endA && posB < endB) {
                int valB = neighbors[posB];
                posA = Arrays.binarySearch(neighbors, posA, endA, valB);
                if (posA < 0) {
                    posA = -posA - 1;
                    posB++;
                }
                else {
                    posA++;
                    posB++;
                    next = valB;
                    return true;
                }
            }

            return false;
        }

        public boolean findNextImbalancedB() {
            while (posA < endA && posB < endB) {
                int valA = neighbors[posA];
                posB = Arrays.binarySearch(neighbors, posB, endB, valA);
                if (posB < 0) {
                    posB = -posB - 1;
                    posA++;
                }
                else {
                    posA++;
                    posB++;
                    next = valA;
                    return true;
                }
            }

            return false;
        }


        /** Return the number of edges in this range that overlap with the given range.
         * Mutates the position of the given range.
         * */
        public int overlapSize() {
            int size = 0;
            while (findNext())
                size++;
            return size;
        }

        /** Return the Jaccard similarity between this range and the given range.
         * Mutates the position of the given range.
         * */
        public float jaccardSimilarity() {
            int rs = rangeSize();
            if (rs == 0) return 0;

            return overlapSize() / (float) rs;
        }

        public EdgeRange aEdges() {
            return new EdgeRange(neighbors, startA, endA);
        }

        public EdgeRange bEdges() {
            return new EdgeRange(neighbors, startB, endB);
        }

        /** Reset the position of this range to the start of the range. */
        public void reset() {
            posA = startA;
            posB = startB;
            next = Integer.MIN_VALUE;
        }

        public int nextInternalId() {
            return next;
        }

        public int rangeSize() {
            return endA - startA + endB - startB;
        }

        public int minSubRangeSize() {
            return Math.min(endA - startA,  endB - startB);
        }
        public int maxSubRangeSize() {
            return Math.max(endA - startA,  endB - startB);
        }
    }
 }
