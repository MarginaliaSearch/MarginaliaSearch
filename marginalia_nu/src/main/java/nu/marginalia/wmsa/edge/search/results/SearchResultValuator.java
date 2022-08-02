package nu.marginalia.wmsa.edge.search.results;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.util.language.WordPatterns;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchResultKeywordScore;

import java.util.List;
import java.util.regex.Pattern;

@Singleton
public class SearchResultValuator {
    private final NGramDict dict;

    private static final Pattern separator = Pattern.compile("_");

    private static final int MIN_LENGTH = 500;
    private static final int AVG_LENGTH = 1400;

    @Inject
    public SearchResultValuator(NGramDict dict) {
        this.dict = dict;
    }


    // This is basically a bargain bin BM25
    public double evaluateTerms(List<EdgeSearchResultKeywordScore> rawScores, IndexBlock block, int length) {
        EdgeSearchResultKeywordScore[] scores = rawScores.stream().filter(w -> !w.keyword.contains(":")).toArray(EdgeSearchResultKeywordScore[]::new);

        if (scores.length == 0) {
            return IndexBlock.Words.sortOrder;
        }

        final double[] weights = getTermWeights(scores);
        final double lengthPenalty = getLengthPenalty(length);

        double termSum = 0.;
        double factorSum = 0.;

        for (int i = 0; i < scores.length; i++) {
            final double factorBase;

            if (scores[i].link) factorBase = 0.5;
            else factorBase = 1.;

            final double factor = factorBase / (1.0 + weights[i]);

            factorSum += factor;

            double termValue = (scores[i].index.sortOrder + 0.5) * factor;

            if (!scores[i].link && !scores[i].title) {
                termValue *= lengthPenalty;
            }

            termSum += termValue;
        }

        assert factorSum != 0 ;

        if (block == IndexBlock.Title || block == IndexBlock.TitleKeywords) {
            return block.sortOrder + (termSum / factorSum) / 5;
        }

        return termSum / factorSum;
    }

    private double getLengthPenalty(int length) {
        if (length < MIN_LENGTH) {
            length = MIN_LENGTH;
        }
        return (0.7 + 0.3 * length / AVG_LENGTH);
    }

    private double[] getTermWeights(EdgeSearchResultKeywordScore[] scores) {
        double[] weights = new double[scores.length];

        for (int i = 0; i < scores.length; i++) {
            String[] parts = separator.split(scores[i].keyword);
            double sumScore = 0.;

            int count = 0;
            for (String part : parts) {
                if (!WordPatterns.isStopWord(part)) {
                    sumScore += dict.getTermFreq(part);
                    count++;
                }
            }
            if (count == 0) count = 1;

            weights[i] = Math.sqrt(sumScore)/count;
        }

        return weights;
    }
}
