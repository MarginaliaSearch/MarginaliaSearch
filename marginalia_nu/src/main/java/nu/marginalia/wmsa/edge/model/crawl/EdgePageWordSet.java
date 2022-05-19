package nu.marginalia.wmsa.edge.model.crawl;

import lombok.Data;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;

import java.util.*;

@Data
public class EdgePageWordSet {
    public final Map<IndexBlock, EdgePageWords> wordSets;

    public EdgePageWordSet(EdgePageWords... words) {
        wordSets = new EnumMap<>(IndexBlock.class);
        for (EdgePageWords w : words) {
            wordSets.put(w.block, w);
        }
    }

    public EdgePageWords get(IndexBlock block) {
        var words = wordSets.get(block);
        if (words == null) {
            return new EdgePageWords(block);
        }
        return words;
    }

    public void append(IndexBlock block, Collection<String> words) {
        wordSets.computeIfAbsent(block, b -> new EdgePageWords(block)).addAll(words);
    }

    public Collection<EdgePageWords> values() {
        return new ArrayList<>(wordSets.values());
    }

    public boolean isEmpty() {
        return 0 == wordSets.values().stream().mapToInt(EdgePageWords::size).sum();
    }

    public String toString() {
        var sj = new StringJoiner("\n", "EdgePageWordSet:\n", "");
        wordSets.forEach((block, words) -> {
            if (words.size() > 0) {
                sj.add("\t" + block + "\t" + words.getWords());
            }
        });
        return sj.toString();
    }
}
