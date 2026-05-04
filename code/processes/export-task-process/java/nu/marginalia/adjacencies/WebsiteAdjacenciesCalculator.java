package nu.marginalia.adjacencies;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.domaingraph.*;
import nu.marginalia.process.ProcessConfiguration;
import nu.marginalia.process.control.ProcessHeartbeatImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class WebsiteAdjacenciesCalculator {
    private final GraphSource linkGraphSource;

    private final ProcessConfiguration configuration;
    private final HikariDataSource dataSource;
    private static final Logger logger = LoggerFactory.getLogger(WebsiteAdjacenciesCalculator.class);

    float[] weights;

    @Inject
    public WebsiteAdjacenciesCalculator(LinkGraphSource forwardLinkGraphSource,
                                        ProcessConfiguration configuration,
                                        HikariDataSource dataSource) {
        this.linkGraphSource = forwardLinkGraphSource;

        this.configuration = configuration;
        this.dataSource = dataSource;
    }


    // for testing
    public WebsiteAdjacenciesCalculator(GraphSource forwardLinkGraphSource) {

        this.linkGraphSource = forwardLinkGraphSource;
        this.configuration = null;
        this.dataSource = null;
    }



    public void export() throws Exception {
        try (var processHeartbeat = new ProcessHeartbeatImpl(configuration, dataSource)) {
            AdjacenciesLoader loader = new AdjacenciesLoader(dataSource);
            run(loader::load);
            loader.stop();
        }
    }

    public void run(Consumer<DomainSimilarities> consumer) throws Exception {

        logger.info("Loading graph");
        DomainGraph graph = linkGraphSource.getGraph();

        logger.info("Calculating edge bloom filter");

        // Create a bloom filter for the incoming edge sets for each vertex.
        // size is 4 * 64 bits ~ 700 MB with prod data, which is expected to
        // cover the average degree of a vertex.
        // (... which is pareto distributed, bigger we go the faster the edge cases go,
        //   but the edge cases are also more likely to have true positive overlaps)

        try (EdgeBloomFilter bloomFilter = graph.inEdgesBloomFilter(4)) {

            logger.info("Calculating weights for {} domains", graph.size());
            float[] weights = new float[graph.size()];
            for (int i = 0; i < weights.length; i++) {
                int degree = graph.inDegree(i);
                weights[i] = 1.0f / (float) Math.log(2 + degree);
            }

            logger.info("Calculating similarities for {} domains", graph.size());

            AtomicInteger progress = new AtomicInteger(0);
            AtomicInteger output = new AtomicInteger(0);

            IntStream.range(0, weights.length)
                    .parallel()
                    .forEach(iv -> {
                        int progressVal = progress.incrementAndGet();

                        if (progressVal % 10000 == 0) {
                            logger.info("Calculating similarities: {}/{}: {}", progressVal, weights.length, output.get());
                        }

                        LongList encodedSimilarIds = new LongArrayList();
                        IntSet considered = new IntOpenHashSet();

                        // We're going to find domains the current domain is linking to,
                        // and then looking back at the set of domains that those domain's links are coming from

                        var candidateSourceEdges = graph.inEdges(iv);

                        while (candidateSourceEdges.hasNext()) {
                            int cv = candidateSourceEdges.nextInternalId();

                            if (iv == cv)
                                continue;

                            var candidateEdges = graph.outEdges(cv);

                            // This website is very widely linked, and not interesting
                            if (candidateEdges.size() > 1000) {
                                continue;
                            }

                            while (candidateEdges.hasNext()) {

                                int jv = candidateEdges.nextInternalId();
                                if (iv == jv)
                                    continue;

                                if (!bloomFilter.mayOverlap(iv, jv)) continue;

                                if (!considered.add(jv)) continue;

                                var overlap = graph.inOverlapEdges(iv, jv);

                                if (!testJaccard(overlap, 0.1))  {
                                    continue;
                                }

                                float weightedSimilarity = 0.f;
                                float weightedSimilarityA = 0.f;
                                float weightedSimilarityB = 0.f;

                                while (overlap.findNext()) {
                                    weightedSimilarity += weights[overlap.nextInternalId()];
                                }

                                var aEdges = overlap.aEdges();
                                while (aEdges.hasNext()) {
                                    weightedSimilarityA += weights[aEdges.nextInternalId()];
                                }
                                var bEdges = overlap.bEdges();
                                while (bEdges.hasNext()) {
                                    weightedSimilarityB += weights[bEdges.nextInternalId()];
                                }

                                weightedSimilarity /= Math.sqrt(weightedSimilarityA * weightedSimilarityB);
                                if (weightedSimilarity < 0.1) {
                                    continue;
                                }

                                encodedSimilarIds.add(
                                        DomainSimilarities.encode(graph.vertexId(jv), weightedSimilarity)
                                );
                            }
                        }

                        if (!encodedSimilarIds.isEmpty()) {
                            consumer.accept(new DomainSimilarities(graph.vertexId(iv), encodedSimilarIds));
                        }
                    });

            logger.info("Done");
        }
    }

    /** Returns true if the overlap has a jaccard similarity above the given threshold. */
    private boolean testJaccard(DomainGraph.OverlapRange overlap, double jaccardLimit) {
        int minRange = overlap.minSubRangeSize();

        if (minRange < 4)
            return false;

        int maxRange = overlap.maxSubRangeSize();

        // If the ratio is too large, we can't find a similarity above the threshold
        double minMaxSizeRatio = (1-jaccardLimit)/jaccardLimit;

        if (maxRange > minMaxSizeRatio * minRange)
            return false;

        try {
            return overlap.jaccardSimilarity() >= jaccardLimit;
        }
        finally {
            overlap.reset();
        }
    }
}
