package nu.marginalia.adjacencies;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.api.linkgraph.AggregateLinkGraphClient;
import nu.marginalia.process.ProcessConfiguration;
import nu.marginalia.process.control.ProcessHeartbeatImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static nu.marginalia.adjacencies.SparseBitVector.andCardinality;
import static nu.marginalia.adjacencies.SparseBitVector.weightedProduct;

public class WebsiteAdjacenciesCalculator {
    private final AggregateLinkGraphClient domainLinksClient;
    private final ProcessConfiguration configuration;
    private final HikariDataSource dataSource;
    public AdjacenciesData adjacenciesData;
    public DomainAliases domainAliases;
    private static final Logger logger = LoggerFactory.getLogger(WebsiteAdjacenciesCalculator.class);

    float[] weights;

    @Inject
    public WebsiteAdjacenciesCalculator(AggregateLinkGraphClient domainLinksClient,
                                        ProcessConfiguration configuration,
                                        HikariDataSource dataSource) {
        this.domainLinksClient = domainLinksClient;
        this.configuration = configuration;
        this.dataSource = dataSource;
    }

    public void export() throws Exception {
        try (var processHeartbeat = new ProcessHeartbeatImpl(configuration, dataSource)) {
            domainAliases = new DomainAliases(dataSource);
            adjacenciesData = new AdjacenciesData(domainLinksClient, domainAliases);
            weights = adjacenciesData.getWeights();

            AdjacenciesLoader loader = new AdjacenciesLoader(dataSource);
            var executor = Executors.newFixedThreadPool(16);

            int total = adjacenciesData.getIdsList().size();
            AtomicInteger progress = new AtomicInteger(0);
            IntStream.of(adjacenciesData.getIdsList().toArray()).parallel()
                    .filter(domainAliases::isNotAliased)
                    .forEach(id -> {
                        findAdjacent(id, loader::load);
                        processHeartbeat.setProgress(progress.incrementAndGet() / (double) total);
                    });

            executor.shutdown();
            System.out.println("Waiting for wrap-up");
            loader.stop();
        }
    }

    public void findAdjacent(int domainId, Consumer<DomainSimilarities> andThen) {
        findAdjacentDtoS(domainId, andThen);
    }

    double cosineSimilarity(SparseBitVector a, SparseBitVector b) {
        double andCardinality = andCardinality(a, b);
        andCardinality /= Math.sqrt(a.getCardinality());
        andCardinality /= Math.sqrt(b.getCardinality());
        return andCardinality;
    }

    double expensiveCosineSimilarity(SparseBitVector a, SparseBitVector b) {
        return weightedProduct(weights, a, b) / Math.sqrt(a.mulAndSum(weights) * b.mulAndSum(weights));
    }

    public record DomainSimilarities(int domainId, List<DomainSimilarity> similarities) {}

    public record DomainSimilarity(int domainId, double value) {}

    private void findAdjacentDtoS(int domainId, Consumer<DomainSimilarities> andThen) {
        var vector = adjacenciesData.getVector(domainId);

        if (vector == null || !vector.cardinalityExceeds(10)) {
            return;
        }

        List<DomainSimilarity> similarities = new ArrayList<>(1000);

        var items = adjacenciesData.getCandidates(vector);


        int cardMin = Math.max(2, (int) (0.01 * vector.getCardinality()));

        items.forEach(id -> {
            var otherVec = adjacenciesData.getVector(id);

            if (null == otherVec || otherVec == vector)
                return true;

            if (otherVec.getCardinality() < cardMin)
                return true;

            double similarity = cosineSimilarity(vector, otherVec);
            if (similarity > 0.1) {
                var recalculated = expensiveCosineSimilarity(vector, otherVec);
                if (recalculated > 0.1) {
                    similarities.add(new DomainSimilarity(id, recalculated));
                }
            }

            return true;
        });

        if (similarities.size() > 128) {
            similarities.sort(Comparator.comparing(DomainSimilarity::value));
            similarities.subList(0, similarities.size() - 128).clear();
        }


        andThen.accept(new DomainSimilarities(domainId, similarities));
    }

}
