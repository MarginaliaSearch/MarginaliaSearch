package nu.marginalia.functions.searchquery.query_parser.variant.strategy;

import nu.marginalia.functions.searchquery.query_parser.variant.QueryWord;
import nu.marginalia.functions.searchquery.query_parser.variant.VariantStrategy;
import nu.marginalia.util.ngrams.NGramBloomFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Variant strategy that splits tokens at the boundary between a number and a word.
 */
public class SplitWordNum implements VariantStrategy {


    final Pattern numWordBoundary = Pattern.compile("[0-9][a-zA-Z]|[a-zA-Z][0-9]");
    private final NGramBloomFilter nGramBloomFilter;

    public SplitWordNum(NGramBloomFilter nGramBloomFilter) {
        this.nGramBloomFilter = nGramBloomFilter;
    }

    @Override
    public Collection<? extends List<String>> constructVariants(List<QueryWord> ls) {
        List<String> asTokens2 = new ArrayList<>();

        boolean num = false;

        for (var span : ls) {
            var wordMatcher = numWordBoundary.matcher(span.word);
            var stemmedMatcher = numWordBoundary.matcher(span.stemmed);

            int ws = 0;
            int ss = 0;
            boolean didSplit = false;
            while (wordMatcher.find(ws) && stemmedMatcher.find(ss)) {
                ws = wordMatcher.start()+1;
                ss = stemmedMatcher.start()+1;
                if (nGramBloomFilter.isKnownNGram(splitAtNumBoundary(span.word, stemmedMatcher.start(), "_"))
                        || nGramBloomFilter.isKnownNGram(splitAtNumBoundary(span.word, stemmedMatcher.start(), "-")))
                {
                    String combined = splitAtNumBoundary(span.word, wordMatcher.start(), "_");
                    asTokens2.add(combined);
                    didSplit = true;
                    num = true;
                }
            }

            if (!didSplit) {
                asTokens2.add(span.word);
            }
        }

        if (num) {
            return List.of(asTokens2);
        }
        return Collections.emptyList();
    }

    private String splitAtNumBoundary(String in, int splitPoint, String joiner) {
        return in.substring(0, splitPoint+1) + joiner + in.substring(splitPoint+1);
    }
}
