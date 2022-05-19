package nu.marginalia.wmsa.edge.converting.interpreter.instruction;

import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWords;

import java.util.Arrays;

public record DocumentKeywords(IndexBlock block, String... keywords) {
    public DocumentKeywords(EdgePageWords words) {
        this(words.block, words.words.toArray(String[]::new));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()+"["+block +", "+Arrays.toString(keywords)+"]";
    }
}
