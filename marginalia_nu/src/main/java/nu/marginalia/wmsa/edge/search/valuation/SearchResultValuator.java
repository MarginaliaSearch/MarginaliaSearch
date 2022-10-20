package nu.marginalia.wmsa.edge.search.valuation;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.util.language.WordPatterns;
import nu.marginalia.wmsa.edge.assistant.dict.TermFrequencyDict;
import nu.marginalia.wmsa.edge.index.model.EdgePageWordFlags;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchResultKeywordScore;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchSubquery;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
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
            termSum += (index.ordinal() + 0.5) * factor;
        }

        return termSum / factorSum;
    }

    public double evaluateTerms(List<EdgeSearchResultKeywordScore> rawScores, int length, int titleLength) {
        int sets = 1 + rawScores.stream().mapToInt(EdgeSearchResultKeywordScore::set).max().orElse(0);

        double bestPosFactor = 10;
        double bestScore = 10;
        double bestAllTermsFactor = 1.;

        for (int set = 0; set <= sets; set++) {
            SearchResultsKeywordSet keywordSet = createKeywordSet(rawScores, set);

            if (keywordSet == null)
                continue;

            final double lengthPenalty = getLengthPenalty(length);

            final double bm25Factor = getBM25(keywordSet, lengthPenalty);
            final double minCountFactor = getMinCountFactor(keywordSet);
            final double posFactor = posFactor(keywordSet);

            bestScore = min(bestScore, bm25Factor * minCountFactor);
            bestPosFactor = min(bestPosFactor, posFactor);
            bestAllTermsFactor = min(bestAllTermsFactor, getAllTermsFactorForSet(keywordSet, titleLength));
        }

        return (0.7 + 0.3 * bestPosFactor) * bestScore * (0.3 + 0.7 * bestAllTermsFactor);
    }

    private double getMinCountFactor(SearchResultsKeywordSet keywordSet) {
        // Penalize results with few keyword hits

        int min = 32;

        for (var keyword : keywordSet) {
            min = min(min, keyword.count());
        }

        if (min <= 1) return 2;
        if (min <= 2) return 1;
        if (min <= 3) return 0.75;
        return 0.5;
    }

    private double getBM25(SearchResultsKeywordSet keywordSet, double lengthPenalty) {

        // This is a fairly bastardized BM25; the weight factors below are used to
        // transform it on a scale from 0 ... 10; where 0 is best, 10+ is worst.
        //
        // ... for historical reasons
        //

        final double wf1 = 1.0;
        final double wf2 = 2000.;

        double termSum = 0.;
        double factorSum = 0.;

        for (var keyword : keywordSet) {
            double tfIdf = Math.min(255, keyword.tfIdf());
            final double factor = 1.0 / (1.0 + keyword.weight());

            factorSum += factor;
            termSum += (1 + wf1*tfIdf) * factor;
        }

        termSum /= lengthPenalty;

        return Math.sqrt(wf2 / (termSum / factorSum));
    }

    private double posFactor(SearchResultsKeywordSet keywordSet) {
        // Penalize keywords that first appear late in the document

        double avgPos = 0;
        for (var keyword : keywordSet) {
            avgPos += keyword.score().firstPos();
        }
        avgPos /= keywordSet.length();

        return Math.sqrt(1 + avgPos / 3.);
    }


    private double getAllTermsFactorForSet(SearchResultsKeywordSet set, int titleLength) {
        double totalFactor = 1.;

        double totalWeight = 0;
        for (var keyword : set) {
            totalWeight += keyword.weight();
        }

        for (var keyword : set) {
            totalFactor *= getAllTermsFactor(keyword, totalWeight, titleLength);
        }

        return totalFactor;
    }

    private double getAllTermsFactor(SearchResultsKeyword keyword, double totalWeight, int titleLength) {
        double f = 1.;

        final double k = keyword.weight() / totalWeight;

        EnumSet<EdgePageWordFlags> flags = keyword.flags();

        final boolean title = flags.contains(EdgePageWordFlags.Title);
        final boolean site = flags.contains(EdgePageWordFlags.Site);
        final boolean siteAdjacent = flags.contains(EdgePageWordFlags.SiteAdjacent);
        final boolean subject = flags.contains(EdgePageWordFlags.Subjects);
        final boolean names = flags.contains(EdgePageWordFlags.NamesWords);

        if (title) {
            if (titleLength <= 64) {
                f *= Math.pow(0.5, k);
            }
            else if (titleLength < 96) {
                f *= Math.pow(0.75, k);
            }
            else { // likely keyword stuffing if the title is this long
                f *= Math.pow(0.9, k);
            }
        }

        if (site) {
            f *= Math.pow(0.75, k);
        }
        else if (siteAdjacent) {
            f *= Math.pow(0.8, k);
        }

        if (subject) {
            f *= Math.pow(0.8, k);
        }

        if (!title && !subject && names) {
            f *= Math.pow(0.9, k);
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

    private SearchResultsKeywordSet createKeywordSet(List<EdgeSearchResultKeywordScore> rawScores, int thisSet) {
        EdgeSearchResultKeywordScore[] scores = rawScores.stream().filter(w -> w.set() == thisSet && !w.keyword().contains(":")).toArray(EdgeSearchResultKeywordScore[]::new);
        if (scores.length == 0) {
            return null;
        }
        final double[] weights = getTermWeights(scores);

        SearchResultsKeyword[] keywords = new SearchResultsKeyword[scores.length];
        for (int i = 0; i < scores.length; i++) {
            keywords[i] = new SearchResultsKeyword(scores[i], weights[i]);
        }

        return new SearchResultsKeywordSet(keywords);

    }


    private record SearchResultsKeyword(EdgeSearchResultKeywordScore score, double weight) {
        public int tfIdf() {
            return score.metadata().tfIdf();
        }
        public int count() {
            return score.metadata().count();
        }
        public EnumSet<EdgePageWordFlags> flags() {
            return score().metadata().flags();
        }
    }

    private record SearchResultsKeywordSet(
            SearchResultsKeyword[] keywords) implements Iterable<SearchResultsKeyword>
    {
        @NotNull
        @Override
        public Iterator<SearchResultsKeyword> iterator() {
            return Arrays.stream(keywords).iterator();
        }

        public int length() {
            return keywords.length;
        }
    }
}
