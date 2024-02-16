package nu.marginalia.ranking;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import nu.marginalia.ranking.accumulator.RankingResultAccumulator;
import nu.marginalia.ranking.data.GraphSource;
import nu.marginalia.ranking.jgrapht.PersonalizedPageRank;
import org.jgrapht.Graph;
import org.jgrapht.alg.interfaces.VertexScoringAlgorithm;
import org.jgrapht.alg.scoring.PageRank;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class PageRankDomainRanker implements RankingAlgorithm {
    private final List<Integer> influenceSet;
    private final Graph<Integer, ?> graph;

    public PageRankDomainRanker(GraphSource source,
                                List<Integer> influenceSet)
    {
        this.influenceSet = influenceSet;
        this.graph = source.getGraph();
    }

    @Override
    public <T> T calculate(int resultCount, Supplier<RankingResultAccumulator<T>> accumulatorP) {
        VertexScoringAlgorithm<Integer, Double> pageRank;

        if (influenceSet != null && !influenceSet.isEmpty()) {
            pageRank = new PersonalizedPageRank<>(graph, influenceSet);
        }
        else {
            pageRank = new PageRank<>(graph);
        }

        TIntList results = new TIntArrayList(resultCount);
        pageRank.getScores().entrySet()
                .stream()
                .sorted(Comparator.comparing((Map.Entry<Integer, Double> e) -> -e.getValue()))
                .limit(resultCount)
                .map(Map.Entry::getKey)
                .forEach(results::add);

        var accumulator = accumulatorP.get();
        for (int i = 0; i < results.size(); i++) {
            accumulator.add(results.get(i), i);
        }
        return accumulator.get();
    }

}
