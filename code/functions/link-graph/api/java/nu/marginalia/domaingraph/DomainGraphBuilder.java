package nu.marginalia.domaingraph;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.Arrays;
import java.util.EnumSet;

public final class DomainGraphBuilder {
    private final boolean directed;
    private final boolean weighted;
    private final boolean sorted;

    private final IntArrayList vertexIds = new IntArrayList();

    private DomainGraphBuilder(boolean directed,
                               boolean weighted,
                               boolean sorted)
    {
        this.directed = directed;
        this.weighted = weighted;
        this.sorted = sorted;

        if (sorted && weighted)
            throw new IllegalArgumentException("cannot sort and weight the same graph");
    }

    public static DomainGraphBuilder directed() {
        return new DomainGraphBuilder(true, false, true);
    }

    public static DomainGraphBuilder undirectedWeighted() {
        return new DomainGraphBuilder(false, true, false);
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
        if (directed)
            return buildDirected(stream);
        else
            return buildUndirected(stream);

    }

    private DomainGraph buildUndirected(EdgeStream stream) {
        final int[] verts = sortedDedupedVertexIds();

        // Free RAM
        vertexIds.clear();
        vertexIds.trim();

        final int nVertices = verts.length;
        final int[] inOffsets = new int[nVertices + 1];

        stream.emit(new UndirectedCountingConsumer(verts, inOffsets));

        for (int i = 1; i <= nVertices; i++) {
            inOffsets[i] += inOffsets[i - 1];
        }

        final int totalIn = inOffsets[nVertices];
        final int[] inNeighbors = new int[totalIn];
        final double[] inWeights = weighted ? new double[totalIn] : null;
        final int[] inCursor = inOffsets.clone();

        stream.emit(
                new UndirectedFillingConsumer(verts,
                        inCursor, inNeighbors, inWeights)
        );

        if (sorted) {
            for (int i = 0; i < nVertices; i++) {
                Arrays.sort(inNeighbors, inOffsets[i], inOffsets[i + 1]);
            }
        }

        EnumSet<GraphTrait> traits = EnumSet.noneOf(GraphTrait.class);

        if (weighted) traits.add(GraphTrait.WEIGHTED);
        if (sorted) traits.add(GraphTrait.SORTED);
        if (directed) traits.add(GraphTrait.DIRECTED);

        return new DomainGraph(traits,
                verts,
                inOffsets, inNeighbors, inWeights,
                inOffsets, inNeighbors, inWeights
        );
    }

    private DomainGraph buildDirected(EdgeStream stream) {
        final int[] verts = sortedDedupedVertexIds();

        // Free RAM
        vertexIds.clear();
        vertexIds.trim();

        final int nVertices = verts.length;
        final int[] inOffsets = new int[nVertices + 1];
        final int[] outOffsets = new int[nVertices + 1];

        stream.emit(new DirectedCountingConsumer(verts, inOffsets, outOffsets));

        for (int i = 1; i <= nVertices; i++) {
            inOffsets[i] += inOffsets[i - 1];
            outOffsets[i] += outOffsets[i - 1];
        }

        final int totalIn = inOffsets[nVertices];
        final int totalOut = outOffsets[nVertices];

        final int[] inNeighbors = new int[totalIn];
        final int[] outNeighbors = new int[totalOut];

        final double[] inWeights = weighted ? new double[totalIn] : null;
        final double[] outWeights = weighted ? new double[totalOut] : null;

        final int[] inCursor = inOffsets.clone();
        final int[] outCursor = outOffsets.clone();

        stream.emit(
                new DirectedFillingConsumer(verts,
                        inCursor, inNeighbors, inWeights,
                        outCursor, outNeighbors, outWeights)
        );

        if (sorted) {
            for (int i = 0; i < nVertices; i++) {
                Arrays.sort(inNeighbors, inOffsets[i], inOffsets[i + 1]);
                Arrays.sort(outNeighbors, outOffsets[i], outOffsets[i + 1]);
            }
        }

        EnumSet<GraphTrait> traits = EnumSet.noneOf(GraphTrait.class);

        if (weighted) traits.add(GraphTrait.WEIGHTED);
        if (sorted) traits.add(GraphTrait.SORTED);
        if (directed) traits.add(GraphTrait.DIRECTED);

        return new DomainGraph(traits,
                verts,
                inOffsets, inNeighbors, inWeights,
                outOffsets, outNeighbors, outWeights
        );
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

    private final class DirectedCountingConsumer implements EdgeConsumer {
        private final int[] verts;
        private final int[] inOffsets;
        private final int[] outOffsets;

        DirectedCountingConsumer(int[] verts, int[] inOffsets, int[] outOffsets) {
            this.verts = verts;
            this.inOffsets = inOffsets;
            this.outOffsets = outOffsets;
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
            outOffsets[srcIdx + 1]++;
        }
    }


    private final class UndirectedCountingConsumer implements EdgeConsumer {
        private final int[] verts;
        private final int[] inOffsets;

        UndirectedCountingConsumer(int[] verts, int[] inOffsets) {
            this.verts = verts;
            this.inOffsets = inOffsets;
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

            if (source == dest)
                return;

            inOffsets[srcIdx + 1]++;
        }
    }

    private final class UndirectedFillingConsumer implements EdgeConsumer {
        private final int[] verts;

        private final int[] inCursor;
        private final int[] inNeighbors;
        private final double[] inWeights;

        UndirectedFillingConsumer(int[] verts,
                                  int[] inCursor, int[] inNeighbors, double[] inWeights
                        ) {
            this.verts = verts;

            this.inCursor = inCursor;
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

            inNeighbors[inCursor[dstIdx]++] = srcIdx;

            if (source == dest)
                return;

            inNeighbors[inCursor[srcIdx]++] = dstIdx;
        }

        @Override
        public void accept(int source, int dest, double weight) {
            if (!weighted) throw new IllegalStateException("graph is unweighted, no weight expected");

            int srcIdx = Arrays.binarySearch(verts, source);
            if (srcIdx < 0) return;

            int dstIdx = Arrays.binarySearch(verts, dest);
            if (dstIdx < 0) return;

            int dstSlot = inCursor[dstIdx]++;
            inNeighbors[dstSlot] = srcIdx;
            inWeights[dstSlot] = weight;

            if (source == dest)
                return;

            int srcInSlot = inCursor[srcIdx]++;
            inNeighbors[srcInSlot] = dstIdx;
            inWeights[srcInSlot] = weight;
        }
    }



    private final class DirectedFillingConsumer implements EdgeConsumer {
        private final int[] verts;

        private final int[] inCursor;
        private final int[] outCursor;
        private final int[] inNeighbors;
        private final int[] outNeighbors;
        private final double[] inWeights;
        private final double[] outWeights;

        DirectedFillingConsumer(int[] verts,
                                  int[] inCursor, int[] inNeighbors, double[] inWeights,
                                  int[] outCursor, int[] outNeighbors, double[] outWeights
        ) {
            this.verts = verts;

            this.inCursor = inCursor;
            this.inNeighbors = inNeighbors;
            this.inWeights = inWeights;

            this.outCursor = outCursor;
            this.outNeighbors = outNeighbors;
            this.outWeights = outWeights;
        }

        @Override
        public void accept(int source, int dest) {
            if (weighted) throw new IllegalStateException("graph is weighted, expected weight");

            int srcIdx = Arrays.binarySearch(verts, source);
            if (srcIdx < 0) return;

            int dstIdx = Arrays.binarySearch(verts, dest);
            if (dstIdx < 0) return;

            inNeighbors[inCursor[dstIdx]++] = srcIdx;
            outNeighbors[outCursor[srcIdx]++] = dstIdx;
        }

        @Override
        public void accept(int source, int dest, double weight) {
            if (!weighted) throw new IllegalStateException("graph is unweighted, no weight expected");

            int srcIdx = Arrays.binarySearch(verts, source);
            if (srcIdx < 0) return;

            int dstIdx = Arrays.binarySearch(verts, dest);
            if (dstIdx < 0) return;

            int dstSlot = inCursor[dstIdx]++;
            inNeighbors[dstSlot] = srcIdx;
            inWeights[dstSlot] = weight;

            int srcSlot = outCursor[srcIdx]++;
            outNeighbors[srcSlot] = dstIdx;
            outWeights[srcSlot] = weight;
        }
    }
}
