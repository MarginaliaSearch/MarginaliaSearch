package nu.marginalia.ranking;

import nu.marginalia.index.client.model.results.SearchResultRankingContext;
import nu.marginalia.index.client.model.results.SearchResultKeywordScore;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.ranking.factors.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.lang.Math.min;

@Singleton
public class ResultValuator {
    private final TermFlagsFactor termFlagsFactor;
    private final Bm25Factor bm25Factor;
    private final TermCoherenceFactor termCoherenceFactor;

    private final PriorityTermFactor priorityTermFactor;

    @Inject
    public ResultValuator(TermFlagsFactor termFlagsFactor,
                          Bm25Factor bm25Factor,
                          TermCoherenceFactor termCoherenceFactor,
                          PriorityTermFactor priorityTermFactor) {

        this.termFlagsFactor = termFlagsFactor;
        this.bm25Factor = bm25Factor;
        this.termCoherenceFactor = termCoherenceFactor;
        this.priorityTermFactor = priorityTermFactor;
    }

    public double calculateSearchResultValue(List<SearchResultKeywordScore> scores,
                                             int length,
                                             int titleLength,
                                             SearchResultRankingContext ctx)
    {
        int sets = numberOfSets(scores);

        double bestBm25Factor = 10;
        double allTermsFactor = 1.;

        final double priorityTermBonus = priorityTermFactor.calculate(scores);

        for (int set = 0; set <= sets; set++) {
            ResultKeywordSet keywordSet = createKeywordSet(scores, set);

            final double bm25 = bm25Factor.calculate(keywordSet, length, ctx);

            bestBm25Factor = min(bestBm25Factor, bm25);
            allTermsFactor *= getAllTermsFactorForSet(keywordSet, titleLength);

        }

        var meta = docMeta(scores);

        double lenFactor = Math.max(1.0, 2500. / (1.0 + length));

        return bestBm25Factor * (0.4 + 0.6 * allTermsFactor) * priorityTermBonus * lenFactor;
    }

    private Optional<DocumentMetadata> docMeta(List<SearchResultKeywordScore> rawScores) {
        return rawScores
                .stream().map(SearchResultKeywordScore::encodedDocMetadata)
                .map(DocumentMetadata::new).findFirst();
    }

    public double getAllTermsFactorForSet(ResultKeywordSet set, int titleLength) {
        double totalFactor = 1.;

        totalFactor *= termFlagsFactor.calculate(set, titleLength);

        if (set.length() > 1) {
            totalFactor *= 1.0 - 0.5 * termCoherenceFactor.calculate(set);
        }

        assert (Double.isFinite(totalFactor));

        return totalFactor;
    }

    private ResultKeywordSet createKeywordSet(List<SearchResultKeywordScore> rawScores,
                                              int thisSet)
    {
        ArrayList<SearchResultKeywordScore> scoresList = new ArrayList<>(rawScores.size());

        for (var score : rawScores) {
            if (score.subquery != thisSet)
                continue;
            if (score.keyword.contains(":"))
                continue;

            scoresList.add(score);
        }

        return new ResultKeywordSet(scoresList.toArray(SearchResultKeywordScore[]::new));

    }

    private int numberOfSets(List<SearchResultKeywordScore> scores) {
        int maxSet = 0;
        for (var score : scores) {
            maxSet = Math.max(maxSet, score.subquery);
        }
        return 1 + maxSet;
    }

}
