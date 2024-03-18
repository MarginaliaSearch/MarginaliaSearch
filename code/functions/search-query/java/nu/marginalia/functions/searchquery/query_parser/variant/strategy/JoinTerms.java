package nu.marginalia.functions.searchquery.query_parser.variant.strategy;

import ca.rmen.porterstemmer.PorterStemmer;
import nu.marginalia.functions.searchquery.query_parser.variant.QueryWord;
import nu.marginalia.functions.searchquery.query_parser.variant.VariantStrategy;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Variant strategy that merges tokens that are adjacent, where the combined token
 * has a high term frequency.  That way we match 'lawnchair' with 'lawn chair' */
public class JoinTerms implements VariantStrategy {
    private final TermFrequencyDict dict;
    private final PorterStemmer ps;

    public JoinTerms(TermFrequencyDict dict, PorterStemmer ps) {
        this.dict = dict;
        this.ps = ps;
    }

    @Override
    public Collection<? extends List<String>> constructVariants(List<QueryWord> span) {
        List<List<String>> ret = new ArrayList<>();

        for (int i = 0; i < span.size()-1; i++) {
            var a = span.get(i);
            var b = span.get(i+1);

            var stemmed = ps.stemWord(a.word + b.word);

            double scoreCombo = dict.getTermFreqStemmed(stemmed);

            if (scoreCombo > 10000) {
                List<String> asTokens = new ArrayList<>();

                for (int j = 0; j < i; j++) {
                    var word = span.get(j).word;
                    asTokens.add(word);
                }
                {
                    var word = a.word + b.word;
                    asTokens.add(word);
                }
                for (int j = i+2; j < span.size(); j++) {
                    var word = span.get(j).word;
                    asTokens.add(word);
                }

                ret.add(asTokens);
            }

        }

        return ret;
    }
}
