package nu.marginalia.domainranking.data;

import java.util.Arrays;

public final class DomainGraph {
    private final int[] vertexIds;
    private final int[] inOffsets;
    private final int[] inNeighbors;
    private final double[] inWeights;
    private final int[] outDegree;

    public DomainGraph(int[] vertexIds,
                       int[] inOffsets,
                       int[] inNeighbors,
                       double[] inWeights,
                       int[] outDegree) {
        this.vertexIds = vertexIds;
        this.inOffsets = inOffsets;
        this.inNeighbors = inNeighbors;
        this.inWeights = inWeights;
        this.outDegree = outDegree;
    }

    public int size() {
        return vertexIds.length;
    }

    public boolean isWeighted() {
        return inWeights != null;
    }

    public int vertexId(int internalIdx) {
        return vertexIds[internalIdx];
    }

    public int outDegree(int internalIdx) {
        return outDegree[internalIdx];
    }

    public int inOffset(int internalIdx) {
        return inOffsets[internalIdx];
    }

    public int inEnd(int internalIdx) {
        return inOffsets[internalIdx + 1];
    }

    public int[] inNeighborsArray() {
        return inNeighbors;
    }

    public double[] inWeightsArray() {
        return inWeights;
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

        for (int k = inOffsets[dstIdx]; k < inOffsets[dstIdx + 1]; k++) {
            if (inNeighbors[k] == srcIdx) return true;
        }

        return false;
    }

    /** Returns the weight of the edge from {@code source} to {@code dest},
     *  or NaN if either endpoint is unknown or the graph is
     *  unweighted. */
    public double edgeWeight(int source, int dest) {
        if (inWeights == null) return Double.NaN;

        int srcIdx = internalIndex(source);
        if (srcIdx < 0)
            return Double.NaN;

        int dstIdx = internalIndex(dest);
        if (dstIdx < 0)
            return Double.NaN;

        for (int k = inOffsets[dstIdx]; k < inOffsets[dstIdx + 1]; k++) {
            if (inNeighbors[k] == srcIdx) return inWeights[k];
        }

        return Double.NaN;
    }
}
