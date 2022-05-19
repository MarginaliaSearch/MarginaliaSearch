package nu.marginalia.wmsa.edge.model.crawl;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ToString @Getter
public class EdgePageWords {
    public final IndexBlock block;
    public final List<String> words = new ArrayList<>();

    public EdgePageWords(IndexBlock block) {
        this.block = block;
    }
    public EdgePageWords(IndexBlock block, Collection<String> initial) {
        this.block = block;

        addAll(initial);
    }

    public void addAll(Collection<String> words) {
        this.words.addAll(words);
    }
    public void addAllMax(Collection<String> words, int limit) {
        words.stream().limit(limit).forEach(this.words::add);
    }
    public int size() {
        return words.size();
    }
    public void addJust(String word) { words.add(word); }
}
