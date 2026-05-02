package nu.marginalia.domainranking;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import nu.marginalia.domainranking.accumulator.RankingResultAccumulator;
import nu.marginalia.domaingraph.DomainGraph;
import nu.marginalia.domaingraph.GraphSource;

import java.util.List;
import java.util.function.Supplier;

public class PageRankDomainRanker implements RankingAlgorithm {
    private final IntArrayList influenceSet;
    private final DomainGraph graph;

    public PageRankDomainRanker(GraphSource source, List<Integer> influenceSet) {
        this.influenceSet = new IntArrayList(influenceSet.size());
        for (Integer id : influenceSet) this.influenceSet.add(id.intValue());
        this.graph = source.getGraph();
    }

    public static PageRankDomainRanker forDomainNames(GraphSource source, List<String> influenceSet) {
        return new PageRankDomainRanker(source, source.domainIds(influenceSet));
    }

    @Override
    public <T> T calculate(int resultCount, Supplier<RankingResultAccumulator<T>> accumulatorSupplier) {
        var pageRank = new PersonalizedPageRank(graph, influenceSet);
        double[] scores = pageRank.getScores();
        int vertexCount = scores.length;
        int topCount = Math.min(resultCount, vertexCount);


        LongHeapPriorityQueue topEntriesHeap = new LongHeapPriorityQueue(topCount);
        for (int vertexIdx = 0; vertexIdx < vertexCount; vertexIdx++) {
            // To keep allocations down, we can use a bit twiddling trick here.  The bits of a float
            // are such that if we coerce them into an int, equality still holds.  Thus if we tack the
            // score onto the most significant bytes of a long, we can sort the ids without extra allocations.
            int scoreBits = Float.floatToRawIntBits((float) Math.max(0d, scores[vertexIdx]));
            long entry = ((long) scoreBits << 32) | (vertexIdx & 0xFFFFFFFFL);

            if (topEntriesHeap.size() < topCount) {
                topEntriesHeap.enqueue(entry);
            } else if (entry > topEntriesHeap.firstLong()) {
                topEntriesHeap.dequeueLong();
                topEntriesHeap.enqueue(entry);
            }
        }

        // Drain the min-heap into a list; entries come out in ascending score
        // order, so the last entry has the highest rank.
        long[] bestResults = new long[topCount];
        for (int i = bestResults.length - 1; i >= 0; i--) {
            bestResults[i] = topEntriesHeap.dequeueLong();
        }

        var accumulator = accumulatorSupplier.get();
        for (int i = 0; i < bestResults.length; i++) {
            // Strip ranking elements to just get the ID
            int internalId = (int) (bestResults[i] & 0xFFFF_FFFFL);
            accumulator.add(graph.vertexId(internalId), i);
        }

        return accumulator.get();
    }
}
