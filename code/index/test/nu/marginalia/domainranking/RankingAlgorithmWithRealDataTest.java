package nu.marginalia.domainranking;

import nu.marginalia.domainranking.accumulator.RankingResultListAccumulator;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

// Test the ranking algorithm with prod data.  Will not run if the data is not available.
// It's not feasible to include the data in the git repo, as it's ~6 GB of data.
//
// The data is available at
// https://downloads.marginalia.nu/link-test-data.tar.gz
//
@Disabled
class RankingAlgorithmWithRealDataTest {

    /** Test the regular PageRank algorithm. */
    @Test
    public void testRegularPR() {
        if (!TestGraphSourceForLinkData.isAvailable()) {
            return;
        }

        var graphSource = new TestGraphSourceForLinkData();
        var results = new PageRankDomainRanker(graphSource, List.of())
                .calculate(10, RankingResultListAccumulator::new);

        for (int i = 0; i < results.size(); i++) {
            System.out.println(i + " " + graphSource.getName(results.get(i)));
        }
    }

    /** Test the personalized PageRank algorithm. */
    @Test
    public void testRegularPPR() {
        if (!TestGraphSourceForLinkData.isAvailable()) {
            return;
        }

        var graphSource = new TestGraphSourceForLinkData();
        var results = new PageRankDomainRanker(graphSource,
                List.of(1476552) // wiby.me
        )
                .calculate(10, RankingResultListAccumulator::new);

        for (int i = 0; i < results.size(); i++) {
            System.out.println(i + " " + graphSource.getName(results.get(i)));
        }
    }

    /** Test the inverted link graph pagerank algorithm */
    @Test
    public void testInvertedLinkGraph() {
        if (!TestGraphSourceForInvertedLinkData.isAvailable()) {
            return;
        }

        var graphSource = new TestGraphSourceForInvertedLinkData();
        var results = new PageRankDomainRanker(graphSource, List.of())
                .calculate(10, RankingResultListAccumulator::new);

        for (int i = 0; i < results.size(); i++) {
            System.out.println(i + " " + graphSource.getName(results.get(i)));
        }
    }

    /** Test pagerank with similarity data */
    @Test
    public void testSimilarityPR() {
        if (!TestGraphSourceForSimilarityData.isAvailable()) {
            return;
        }

        var graphSource = new TestGraphSourceForSimilarityData();
        var results = new PageRankDomainRanker(graphSource, List.of())
                .calculate(10, RankingResultListAccumulator::new);

        for (int i = 0; i < results.size(); i++) {
            System.out.println(i + " " + graphSource.getName(results.get(i)));
        }
    }

    /** Test personalized pagerank with similarity data */
    @Test
    public void testSimilarityPPR() {
        if (!TestGraphSourceForSimilarityData.isAvailable()) {
            return;
        }

        var graphSource = new TestGraphSourceForSimilarityData();
        var results = new PageRankDomainRanker(graphSource,
                List.of(1476552) // wiby.me
        )
                .calculate(10, RankingResultListAccumulator::new);

        for (int i = 0; i < results.size(); i++) {
            System.out.println(i + " " + graphSource.getName(results.get(i)));
        }
    }



}