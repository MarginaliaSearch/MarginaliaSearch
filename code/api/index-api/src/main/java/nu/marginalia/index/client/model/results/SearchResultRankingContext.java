package nu.marginalia.index.client.model.results;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.ToString;

import java.util.Map;

@ToString
public class SearchResultRankingContext {
    private final int docCount;
    private final Object2IntOpenHashMap<String> termCounts = new Object2IntOpenHashMap<>(10, 0.5f);

    public SearchResultRankingContext(int docCount, Map<String, Integer> termCounts) {
        this.docCount = docCount;
        this.termCounts.putAll(termCounts);
    }

    public int termFreqDocCount() {
        return docCount;
    }

    public int frequency(String keyword) {
        return termCounts.getOrDefault(keyword, 1);
    }
}
