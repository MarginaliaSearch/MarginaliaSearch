package nu.marginalia.ranking.results.factors;

import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.results.ResultRankingContext;
import nu.marginalia.sequence.GammaCodedSequence;
import nu.marginalia.sequence.SequenceOperations;

/** Rewards documents where terms appear frequently within the same sentences
 */
public class TermCoherenceFactor {

    public double calculateAvgMinDistance(CompiledQuery<GammaCodedSequence> positions, ResultRankingContext ctx) {
        double sum = 0;
        int cnt = 0;

        for (int i = 0; i < positions.size(); i++) {

            // Skip terms that are not in the regular mask
            if (!ctx.regularMask.get(i))
                continue;

            var posi = positions.at(i);

            // Skip terms that are not in the document
            if (posi == null)
                continue;

            for (int j = i + 1; j < positions.size(); j++) {

                // Skip terms that are not in the regular mask
                if (!ctx.regularMask.get(j))
                    continue;

                var posj = positions.at(j);

                // Skip terms that are not in the document
                if (posj == null)
                    continue;

                int distance = SequenceOperations.minDistance(posi.iterator(), posj.iterator());
                sum += distance;
                cnt++;
            }
        }

        if (cnt > 0) {
            return sum / cnt;
        } else {
            return 1000.;
        }
    }

}