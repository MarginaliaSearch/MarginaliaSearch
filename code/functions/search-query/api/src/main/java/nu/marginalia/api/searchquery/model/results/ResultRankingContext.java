package nu.marginalia.api.searchquery.model.results;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.ToString;

import java.util.Map;

@ToString
public class ResultRankingContext {
    private final int docCount;
    public final ResultRankingParameters params;

    private final Object2IntOpenHashMap<String> fullCounts = new Object2IntOpenHashMap<>(10, 0.5f);
    private final Object2IntOpenHashMap<String> priorityCounts = new Object2IntOpenHashMap<>(10, 0.5f);

    public ResultRankingContext(int docCount,
                                ResultRankingParameters params,
                                Map<String, Integer> fullCounts,
                                Map<String, Integer> prioCounts
                                      ) {
        this.docCount = docCount;
        this.params = params;
        this.fullCounts.putAll(fullCounts);
        this.priorityCounts.putAll(prioCounts);
    }

    public int termFreqDocCount() {
        return docCount;
    }

    public int frequency(String keyword) {
        return fullCounts.getOrDefault(keyword, 1);
    }

    public int priorityFrequency(String keyword) {
        return priorityCounts.getOrDefault(keyword, 1);
    }
}
