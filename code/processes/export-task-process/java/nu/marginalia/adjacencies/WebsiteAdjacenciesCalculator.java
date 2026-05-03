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
    public AdjacenciesData adjacenciesData;
    public DomainAliases domainAliases;
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

        float[] weights = new float[graph.size()];
        long[] bloomHashes = new long[graph.size()];

        logger.info("Calculating weights for {} domains", graph.size());

        for (int i = 0; i < weights.length; i++) {
            int degree = graph.inDegree(i);
            weights[i] = 1.0f / (float) Math.log(2+degree);
        }

        logger.info("Calculating bloom hashes for {} domains", graph.size());

        for (int i = 0; i < bloomHashes.length; i++) {
            DomainGraph.EdgeRange range = graph.inEdges(i);
            bloomHashes[i] = range.bloomHash();
        }

        logger.info("Calculating similarities for {} domains", graph.size());

        AtomicInteger progress = new AtomicInteger(0);
        AtomicInteger output = new AtomicInteger(0);

        IntStream.range(0, bloomHashes.length)
                .parallel()
                .forEach(iv -> {
                    int progressVal = progress.incrementAndGet();

                    if (progressVal % 10000 == 0) {
                        logger.info("Calculating similarities: {}/{}: {}", progressVal, bloomHashes.length, output.get());
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

                            if (0 == (bloomHashes[iv] & bloomHashes[jv])) continue;

                            if (!considered.add(jv)) continue;

                            var overlap = graph.inOverlapEdges(iv, jv);

                            // Too small to say much about similarity
                            if (overlap.rangeSize() < 5)
                                continue;

                            float jaccardSimilarity = overlap.jaccardSimilarity();

                            if (jaccardSimilarity < 0.1) {
                                continue;
                            }
                            float weightedSimilarity = 0.f;
                            float weightedSimilarityA = 0.f;
                            float weightedSimilarityB = 0.f;

                            overlap.reset();

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
    }
}
