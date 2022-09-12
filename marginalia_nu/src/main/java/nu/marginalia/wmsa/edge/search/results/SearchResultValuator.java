package nu.marginalia.wmsa.edge.search.results;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.util.language.WordPatterns;
import nu.marginalia.wmsa.edge.assistant.dict.TermFrequencyDict;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.model.IndexBlockType;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchResultKeywordScore;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchSubquery;

import java.util.List;
import java.util.regex.Pattern;

import static java.lang.Math.min;

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


    public double preEvaluate(EdgeSearchSubquery sq) {
        final String[] terms = sq.searchTermsInclude.stream().filter(f -> !f.contains(":")).toArray(String[]::new);
        final IndexBlock index = sq.block;

        double termSum = 0.;
        double factorSum = 0.;

        final double[] weights = getTermWeights(terms);

        for (int i = 0; i < terms.length; i++) {
            final double factor = 1. / (1.0 + weights[i]);

            factorSum += factor;
            termSum += (index.sortOrder + 0.5) * factor;
        }

        return termSum / factorSum;
    }

    public double evaluateTerms(List<EdgeSearchResultKeywordScore> rawScores, int length, int titleLength) {
        int sets = 1 + rawScores.stream().mapToInt(EdgeSearchResultKeywordScore::set).max().orElse(0);

        double bestScore = 1000;
        double bestAllTermsFactor = 1.;

        int termCount = 5;

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

            double allTermsFactor = 1.0;

            for (int i = 0; i < scores.length; i++) {

                final double factor = 1. / (1.0 + weights[i]);

                factorSum += factor;
                termSum += (scores[i].index().sortOrder + 0.5) * factor / lengthPenalty;

            }

            assert factorSum != 0;

            double value = termSum / factorSum;

            for (int i = 0; i < scores.length; i++) {
                final double factor = 1. / (1.0 + weights[i]);

                allTermsFactor *= getAllTermsFactorForScore(scores[i], scores[i].index(), factor/factorSum, scores.length, titleLength);
            }

            termCount = min(termCount, scores.length);
            bestAllTermsFactor = min(bestAllTermsFactor, allTermsFactor);
            bestScore = min(bestScore, value);
        }

        return bestScore * bestAllTermsFactor  * Math.sqrt(1. + termCount);
    }

    private double getAllTermsFactorForScore(EdgeSearchResultKeywordScore score, IndexBlock block, double termWeight, int scoreCount, int titleLength) {
        double f = 1.;


        if (score.link()) {
            f *= Math.pow(0.5, termWeight / scoreCount);
        }

        if (score.title()) {
            if (block.type.equals(IndexBlockType.PAGE_DATA)) {
                f *= Math.pow(0.8, termWeight / scoreCount);
            }
            else if (titleLength <= 64) {
                f *= Math.pow(0.5, termWeight / scoreCount);
            }
            else if (titleLength < 96) {
                f *= Math.pow(0.75, termWeight / scoreCount);
            }
            else { // likely keyword stuffing if the title is this long
                f *= Math.pow(0.9, termWeight / scoreCount);
            }
        }

        if (!block.type.equals(IndexBlockType.TF_IDF)) {
            if (score.high()) {
                f *= Math.pow(0.75, termWeight / scoreCount);
            } else if (score.mid()) {
                f *= Math.pow(0.8, termWeight / scoreCount);
            } else if (score.low()) {
                f *= Math.pow(0.9, termWeight / scoreCount);
            }
        }

        if (score.site()) {
            f *= Math.pow(0.75, termWeight / scoreCount);
        }

        if (score.subject()) {
            f *= Math.pow(0.8, termWeight / scoreCount);
        }

        if (!score.title() && !score.subject() && score.name()) {
            f *= Math.pow(0.9, termWeight / scoreCount);
        }

        return f;
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


    private double[] getTermWeights(String[] words) {
        double[] weights = new double[words.length];

        for (int i = 0; i < words.length; i++) {
            String[] parts = separator.split(words[i]);
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
