package nu.marginalia.wmsa.edge.search.results;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.util.language.WordPatterns;
import nu.marginalia.wmsa.edge.assistant.dict.TermFrequencyDict;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchResultKeywordScore;

import java.util.List;
import java.util.regex.Pattern;

@Singleton
public class SearchResultValuator {
    private final TermFrequencyDict dict;

    private static final Pattern separator = Pattern.compile("_");

    private static final int MIN_LENGTH = 2000;
    private static final int AVG_LENGTH = 5000;

    @Inject
    public SearchResultValuator(TermFrequencyDict dict) {
        this.dict = dict;
    }


    // This is basically a bargain bin BM25
    public double evaluateTerms(List<EdgeSearchResultKeywordScore> rawScores, IndexBlock block, int length, int titleLength) {
        int sets = 1 + rawScores.stream().mapToInt(EdgeSearchResultKeywordScore::set).max().orElse(0);

        double bestScore = 1000;
        double bestLtsFactor = 1.;

        for (int set = 0; set <= sets; set++) {
            int thisSet = set;
            EdgeSearchResultKeywordScore[] scores = rawScores.stream().filter(w -> w.set() == thisSet && !w.keyword().contains(":")).toArray(EdgeSearchResultKeywordScore[]::new);

            if (scores.length == 0) {
                continue;
            }

            final double[] weights = getTermWeights(scores);
            final double lengthPenalty = getLengthPenalty(length);

            double termSum = 0.;
            double factorSum = 0.;

            double ltsFactor = 1.0;

            for (int i = 0; i < scores.length; i++) {

                final double factor = 1. / (1.0 + weights[i]);

                factorSum += factor;

                double termValue = (scores[i].index().sortOrder + 0.5) * factor;

                termValue /= lengthPenalty;

                if (scores[i].link()) {
                    ltsFactor *= Math.pow(0.5, 1. / scores.length);
                }
                if (scores[i].title()) {
                    if (titleLength <= 64) {
                        ltsFactor *= Math.pow(0.5, 1. / scores.length);
                    }
                    else if (titleLength < 96) {
                        ltsFactor *= Math.pow(0.75, 1. / scores.length);
                    }
                    else {
                        ltsFactor *= Math.pow(0.9, 1. / scores.length);
                    }
                }
                if (scores[i].subject()) {
                    ltsFactor *= Math.pow(0.8, 1. / scores.length);
                }

                termSum += termValue;
            }

            assert factorSum != 0;

            double value = termSum / factorSum;

            bestLtsFactor = Math.min(bestLtsFactor, ltsFactor);
            bestScore = Math.min(bestScore, value);
        }

        return (0.7+0.3*block.sortOrder)*bestScore * bestLtsFactor;
    }

    private double getLengthPenalty(int length) {
        if (length < MIN_LENGTH) {
            length = MIN_LENGTH;
        }
        if (length > AVG_LENGTH) {
            length = AVG_LENGTH;
        }
        return (0.5 + 0.5 * length / AVG_LENGTH);
    }

    private double[] getTermWeights(EdgeSearchResultKeywordScore[] scores) {
        double[] weights = new double[scores.length];

        for (int i = 0; i < scores.length; i++) {
            String[] parts = separator.split(scores[i].keyword());
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
