package nu.marginalia.adjacencies;

import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.ProcessConfiguration;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.process.control.ProcessHeartbeatImpl;
import nu.marginalia.query.client.QueryClient;
import nu.marginalia.service.module.DatabaseModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static nu.marginalia.adjacencies.SparseBitVector.*;

public class WebsiteAdjacenciesCalculator {
    private final HikariDataSource dataSource;
    public AdjacenciesData adjacenciesData;
    public DomainAliases domainAliases;
    private static final Logger logger = LoggerFactory.getLogger(WebsiteAdjacenciesCalculator.class);

    float[] weights;
    public WebsiteAdjacenciesCalculator(QueryClient queryClient, HikariDataSource dataSource) throws SQLException {
        this.dataSource = dataSource;

        domainAliases = new DomainAliases(dataSource);
        adjacenciesData = new AdjacenciesData(queryClient, domainAliases);
        weights = adjacenciesData.getWeights();
    }

    @SneakyThrows
    public void tryDomains(String... domainName) {
        var dataStoreDao = new DbDomainQueries(dataSource);

        System.out.println(Arrays.toString(domainName));

        int[] domainIds = Arrays.stream(domainName).map(EdgeDomain::new)
                .mapToInt(dataStoreDao::getDomainId)
                .map(domainAliases::deAlias)
                .toArray();

        for (int domainId : domainIds) {
            findAdjacentDtoS(domainId, similarities -> {
                for (var similarity : similarities.similarities()) {
                    System.out.println(dataStoreDao.getDomain(similarity.domainId).map(Object::toString).orElse("") + " " + prettyPercent(similarity.value));
                }
            });
        }
    }

    private String prettyPercent(double val) {
        return String.format("%2.2f%%", 100. * val);
    }

    @SneakyThrows
    public void loadAll(ProcessHeartbeat processHeartbeat) {
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

    private static class ProgressPrinter {

        private final AtomicInteger progress;
        private final int total;
        volatile boolean running = false;
        private Thread printerThread;

        private ProgressPrinter(int total) {
            this.total = total;
            this.progress = new AtomicInteger(0);
        }

        public void advance() {
            progress.incrementAndGet();
        }

        private void run() {
            while (running) {
                double value = 100 * progress.get() / (double) total;
                System.out.printf("\u001b[2K\r%3.2f%%", value);
                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
        public void start() {
            running = true;
            printerThread = new Thread(this::run);
            printerThread.setDaemon(true);
            printerThread.start();
        }

        public void stop() throws InterruptedException {
            running = false;
            printerThread.join();
            System.out.println();
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

    public record DomainSimilarities(int domainId, List<DomainSimilarity> similarities) {};
    public record DomainSimilarity(int domainId, double value) {};

    @SneakyThrows
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


    public static void main(String[] args) throws SQLException {
        DatabaseModule dm = new DatabaseModule(false);

        var dataSource = dm.provideConnection();
        var qc = new QueryClient();

        var main = new WebsiteAdjacenciesCalculator(qc, dataSource);

        if (args.length == 1 && "load".equals(args[0])) {
            var processHeartbeat = new ProcessHeartbeatImpl(
                    new ProcessConfiguration("website-adjacencies-calculator", 0, UUID.randomUUID()),
                    dataSource
            );

            try {
                processHeartbeat.start();
                main.loadAll(processHeartbeat);
            }
            catch (Exception ex) {
                logger.error("Failed to load", ex);
            }
            finally {
                processHeartbeat.shutDown();
            }
            return;
        }

        for (;;) {
            String domains = System.console().readLine("> ");

            if (domains.isBlank())
                break;

            var parts = domains.split("\\s+,\\s+");
            try {
                main.tryDomains(parts);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }

//
//        if (args.length == 0) {
//            main.loadAll();
//        }
//        else {
//            main.tryDomains(args);
//        }
    }

}
