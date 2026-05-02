package nu.marginalia.domaingraph;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.Arrays;

public final class DomainGraphBuilder {
    private final boolean directed;
    private final boolean weighted;
    private final IntArrayList vertexIds = new IntArrayList();

    private DomainGraphBuilder(boolean directed, boolean weighted) {
        this.directed = directed;
        this.weighted = weighted;
    }

    public static DomainGraphBuilder directed() {
        return new DomainGraphBuilder(true, false);
    }

    public static DomainGraphBuilder undirectedWeighted() {
        return new DomainGraphBuilder(false, true);
    }

    public DomainGraphBuilder addVertex(int id) {
        vertexIds.add(id);
        return this;
    }

    /** Streaming edge source.  May be invoked multiple times. */
    @FunctionalInterface
    public interface EdgeStream {
        void emit(EdgeConsumer consumer);
    }

    public interface EdgeConsumer {
        void accept(int source, int dest);
        void accept(int source, int dest, double weight);
    }

    /** Builds the graph.  Note that the edges of the stream are provided via a callback,
     * that is run *twice*, this lets us avoid multiple redundant copies of the edge data
     * in RAM.
     */
    public DomainGraph build(EdgeStream stream) {
        final int[] verts = sortedDedupedVertexIds();

        // Free RAM
        vertexIds.clear();
        vertexIds.trim();

        final int nVertices = verts.length;
        final int[] inOffsets = new int[nVertices + 1];
        final int[] outDegree = new int[nVertices];

        stream.emit(new CountingConsumer(verts, inOffsets, outDegree));

        for (int i = 1; i <= nVertices; i++) inOffsets[i] += inOffsets[i - 1];

        final int totalIn = inOffsets[nVertices];
        final int[] inNeighbors = new int[totalIn];
        final double[] inWeights = weighted ? new double[totalIn] : null;
        final int[] cursor = inOffsets.clone();

        stream.emit(new FillingConsumer(verts, cursor, inNeighbors, inWeights));

        return new DomainGraph(verts, inOffsets, inNeighbors, inWeights, outDegree);
    }

    private int[] sortedDedupedVertexIds() {
        int[] verts = vertexIds.toIntArray();
        if (verts.length == 0) return verts;

        Arrays.sort(verts);

        // Deduplicate

        int n = 1;
        for (int i = 1; i < verts.length; i++) {
            if (verts[i] != verts[i - 1])
                verts[n++] = verts[i];
        }

        if (n == verts.length) {
            return verts;
        }
        return Arrays.copyOf(verts, n);
    }

    private final class CountingConsumer implements EdgeConsumer {
        private final int[] verts;
        private final int[] inOffsets;
        private final int[] outDegree;

        CountingConsumer(int[] verts, int[] inOffsets, int[] outDegree) {
            this.verts = verts;
            this.inOffsets = inOffsets;
            this.outDegree = outDegree;
        }

        @Override
        public void accept(int source, int dest) {
            if (weighted)
                throw new IllegalStateException("graph is weighted, expected weight");

            count(source, dest);
        }

        @Override
        public void accept(int source, int dest, double weight) {
            if (!weighted)
                throw new IllegalStateException("graph is unweighted, no weight expected");

            count(source, dest);
        }

        private void count(int source, int dest) {
            int srcIdx = Arrays.binarySearch(verts, source);
            if (srcIdx < 0) return;

            int dstIdx = Arrays.binarySearch(verts, dest);
            if (dstIdx < 0) return;

            inOffsets[dstIdx + 1]++;
            outDegree[srcIdx]++;

            if (!directed) {
                inOffsets[srcIdx + 1]++;
                outDegree[dstIdx]++;
            }
        }
    }

    private final class FillingConsumer implements EdgeConsumer {
        private final int[] verts;
        private final int[] cursor;
        private final int[] inNeighbors;
        private final double[] inWeights;

        FillingConsumer(int[] verts, int[] cursor, int[] inNeighbors, double[] inWeights) {
            this.verts = verts;
            this.cursor = cursor;
            this.inNeighbors = inNeighbors;
            this.inWeights = inWeights;
        }

        @Override
        public void accept(int source, int dest) {
            if (weighted) throw new IllegalStateException("graph is weighted, expected weight");

            int srcIdx = Arrays.binarySearch(verts, source);
            if (srcIdx < 0) return;

            int dstIdx = Arrays.binarySearch(verts, dest);
            if (dstIdx < 0) return;

            inNeighbors[cursor[dstIdx]++] = srcIdx;

            if (!directed) {
                inNeighbors[cursor[srcIdx]++] = dstIdx;
            }
        }

        @Override
        public void accept(int source, int dest, double weight) {
            if (!weighted) throw new IllegalStateException("graph is unweighted, no weight expected");

            int srcIdx = Arrays.binarySearch(verts, source);
            if (srcIdx < 0) return;

            int dstIdx = Arrays.binarySearch(verts, dest);
            if (dstIdx < 0) return;

            int dstSlot = cursor[dstIdx]++;
            inNeighbors[dstSlot] = srcIdx;
            inWeights[dstSlot] = weight;

            if (!directed) {
                int srcSlot = cursor[srcIdx]++;
                inNeighbors[srcSlot] = dstIdx;
                inWeights[srcSlot] = weight;
            }
        }
    }
}
