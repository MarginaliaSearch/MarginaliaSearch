package nu.marginalia.ranking.factors;

import nu.marginalia.index.client.model.results.SearchResultKeywordScore;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.ranking.ResultKeywordSet;

public class TermFlagsFactor {

    public double calculate(ResultKeywordSet set, int titleLength) {

        double totalFactorInvertSum = 0;

        for (var keyword : set) {
            double termFactor = calculateSingleTerm(keyword, titleLength);

            assert (termFactor != 0.);

            totalFactorInvertSum += 1 / (termFactor);
        }

        if (totalFactorInvertSum == 0.) {
            return 1.;
        }

        return set.length() / totalFactorInvertSum;
    }

    public double calculateSingleTerm(SearchResultKeywordScore keyword, int titleLength) {
        double f = 1.;

        int posCount = keyword.positionCount();

        final boolean title = keyword.hasTermFlag(WordFlags.Title);
        final boolean site = keyword.hasTermFlag(WordFlags.Site);
        final boolean siteAdjacent = keyword.hasTermFlag(WordFlags.SiteAdjacent);
        final boolean urlDomain = keyword.hasTermFlag(WordFlags.UrlDomain);
        final boolean urlPath = keyword.hasTermFlag(WordFlags.UrlPath);

        final boolean names = keyword.hasTermFlag(WordFlags.NamesWords);
        final boolean subject = keyword.hasTermFlag(WordFlags.Subjects);

        if (title) {
            f *= titleFactor(titleLength);
        }

        if (posCount != 0) {
            if (site) {
                f *= 0.75;
            } else if (siteAdjacent) {
                f *= 0.8;
            }

            if (subject) {
                f *= 0.8;
            }
            else if (names) {
                f *= 0.85;
            }
        }
        assert (Double.isFinite(f));
        if (urlDomain) {
            f *= 0.8;
        }
        else if (urlPath && posCount > 1) {
            f *= 0.9;
        }
        assert (Double.isFinite(f));

        return f;
    }

    static double titleFactor(int titleLength) {
        if (titleLength <= 64) {
            return 0.5;
        }
        else if (titleLength < 96) {
            return 0.75;
        }

        // likely keyword stuffing if the title is this long
        return 0.9;

    }

}
