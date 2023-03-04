package nu.marginalia.browse.experimental;

import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.TIntHashSet;
import lombok.SneakyThrows;
import nu.marginalia.model.dbcommon.DbDomainQueries;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.id.EdgeId;
import nu.marginalia.service.module.DatabaseModule;
import org.roaringbitmap.RoaringBitmap;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static nu.marginalia.browse.experimental.AndCardIntSet.*;

public class EdgeDomainLinkConsineSimilarityMain {
    ArrayList<Integer> idsList = new ArrayList<>(100_000);
    ArrayList<AndCardIntSet> itemsList = new ArrayList<>(100_000);
    TIntObjectHashMap<AndCardIntSet> dToSMap = new TIntObjectHashMap<>(100_000);
    TIntIntHashMap aliasMap = new TIntIntHashMap(100_000, 0.75f, -1, -1);
    TIntHashSet indexed = new TIntHashSet(100_000);

    float weights[];

    private HikariDataSource dataSource;

    public EdgeDomainLinkConsineSimilarityMain(HikariDataSource dataSource) throws SQLException {
        this.dataSource = dataSource;

        Map<Integer, RoaringBitmap> tmpMap = new HashMap<>(100_000);
        try (
             var conn = dataSource.getConnection();
             var aliasStmt = conn.prepareStatement("SELECT ID, DOMAIN_ALIAS FROM EC_DOMAIN WHERE DOMAIN_ALIAS IS NOT NULL");
             var indexedStmt = conn.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE INDEXED>0");
             var linksStmt = conn.prepareStatement("SELECT SOURCE_DOMAIN_ID, DEST_DOMAIN_ID FROM EC_DOMAIN_LINK")) {
            ResultSet rsp;

            aliasStmt.setFetchSize(10_000);
            rsp = aliasStmt.executeQuery();
            while (rsp.next()) {
                aliasMap.put(rsp.getInt(1), rsp.getInt(2));
            }

            indexedStmt.setFetchSize(10_000);
            rsp = indexedStmt.executeQuery();
            while (rsp.next()) {
                indexed.add(rsp.getInt(1));
            }


            linksStmt.setFetchSize(10_000);
            rsp = linksStmt.executeQuery();
            while (rsp.next()) {
                int source = deAlias(rsp.getInt(1));
                int dest = deAlias(rsp.getInt(2));

                tmpMap.computeIfAbsent(dest, this::createBitmapWithSelf).add(source);
            }
        }

        tmpMap.entrySet().stream()
                .filter(e -> isEligible(e.getValue()))
                .forEach(e -> {
                    var val = of(e.getValue());
                    idsList.add(e.getKey());
                    itemsList.add(val);
                    dToSMap.put(e.getKey(), val);
                });
        weights = new float[1 + idsList.stream().mapToInt(i -> i).max().orElse(0)];
        for (int i = 0; i < idsList.size(); i++) {
            weights[idsList.get(i)] = getWeight(idsList.get(i));
        }
    }

    private boolean isEligible(RoaringBitmap value) {
        int cardinality = value.getCardinality();

        return cardinality < 10000;
    }

    private int deAlias(int id) {
        int val = aliasMap.get(id);
        if (val < 0)
            return id;
        return val;
    }

    LinkedBlockingDeque<DomainSimilarities> similaritiesLinkedBlockingDeque = new LinkedBlockingDeque<>(10);
    volatile boolean running;

    @SneakyThrows
    public void tryDomains(String... domainName) {
        var dataStoreDao = new DbDomainQueries(dataSource);

        System.out.println(Arrays.toString(domainName));

        int[] domainIds = Arrays.stream(domainName).map(EdgeDomain::new)
                .map(dataStoreDao::getDomainId)
                .mapToInt(EdgeId::id)
                .map(this::deAlias)
                .toArray();

        for (int domainId : domainIds) {
            findAdjacentDtoS(domainId, similarities -> {
                for (var similarity : similarities.similarities()) {
                    if (indexed.contains(similarity.domainId)) System.out.print("*");
                    System.out.println(dataStoreDao.getDomain(new EdgeId<>(similarity.domainId)).map(Object::toString).orElse("") + " " + prettyPercent(similarity.value));
                }
            });
        }
    }

    private String prettyPercent(double val) {
        return String.format("%2.2f%%", 100. * val);
    }

    @SneakyThrows
    public void loadAll() {
        running = true;
        var thread = new Thread(this::insertThreadRun);
        thread.start();
        idsList.parallelStream()
                .filter(id -> !aliasMap.containsKey(id))
                .forEach(id -> findAdjacent(id, this::addToQueue));
        running = false;
        thread.join();
    }

    @SneakyThrows
    void addToQueue(DomainSimilarities similarities) {
        similaritiesLinkedBlockingDeque.putLast(similarities);
    }

    public void insertThreadRun() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(
                     """
                     INSERT INTO EC_DOMAIN_NEIGHBORS_2
                     (DOMAIN_ID, NEIGHBOR_ID, RELATEDNESS)
                     VALUES (?, ?, ?)
                     ON DUPLICATE KEY UPDATE RELATEDNESS = GREATEST(EC_DOMAIN_NEIGHBORS_2.RELATEDNESS, VALUES(RELATEDNESS))
                     """)
        ) {
            while (running || !similaritiesLinkedBlockingDeque.isEmpty()) {
                var item = similaritiesLinkedBlockingDeque.pollFirst(60, TimeUnit.SECONDS);
                if (item == null) continue;

                for (var similarity : item.similarities) {
                    stmt.setInt(1, item.domainId);
                    stmt.setInt(2, similarity.domainId);
                    stmt.setDouble(3, similarity.value);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        } catch (SQLException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public RoaringBitmap createBitmapWithSelf(int val) {
        var bm = new RoaringBitmap();
        bm.add(val);
        return bm;
    }

    public void findAdjacent(int domainId, Consumer<DomainSimilarities> andThen) {
        findAdjacentDtoS(domainId, andThen);
    }

    double cosineSimilarity(AndCardIntSet a, AndCardIntSet b) {
        double andCardinality = andCardinality(a, b);
        andCardinality /= Math.sqrt(a.getCardinality());
        andCardinality /= Math.sqrt(b.getCardinality());
        return andCardinality;
    }

    double expensiveCosineSimilarity(AndCardIntSet a, AndCardIntSet b) {
        return weightedProduct(weights, a, b) / Math.sqrt(a.mulAndSum(weights) * b.mulAndSum(weights));
    }

    float getWeight(int i) {
        var vector = dToSMap.get(i);

        if (vector == null) return 1.0f;
        return 1.0f / (float) Math.log(2+vector.getCardinality());
    }

    record DomainSimilarities(int domainId, List<DomainSimilarity> similarities) {};
    record DomainSimilarity(int domainId, double value) {};

    @SneakyThrows
    private void findAdjacentDtoS(int domainId, Consumer<DomainSimilarities> andThen) {
        var vector = dToSMap.get(domainId);
        if (vector == null || !vector.cardinalityExceeds(10)) {
            return;
        }

        System.out.println("DtoS " + domainId);

        List<DomainSimilarity> similarities = new ArrayList<>(1000);

        /** The minimum cardinality a vector can have so that
         *
         * a (x) b
         * ------- < k is given by k^2
         * |a||b|
         *
         */
        int cardMin = Math.max(2, (int) (0.01 * vector.getCardinality()));

        for (int i = 0; i < itemsList.size(); i++) {

            int id = idsList.get(i);
            if (id == domainId)
                continue;

            var otherVec = itemsList.get(i);
            if (otherVec.getCardinality() < cardMin)
                continue;

            double similarity = cosineSimilarity(vector, otherVec);
            if (similarity > 0.1) {
                var recalculated = expensiveCosineSimilarity(vector, otherVec);
                if (recalculated > 0.1) {
                    similarities.add(new DomainSimilarity(id, recalculated));
                }
            }
        }

        if (similarities.size() > 128) {
            similarities.sort(Comparator.comparing(DomainSimilarity::value));
            similarities.subList(0, similarities.size() - 128).clear();
        }


        andThen.accept(new DomainSimilarities(domainId, similarities));
    }


//    @SneakyThrows
//    private void findAdjacentDtoS(Consumer<DomainSimilarities> andThen, int... domainIds) {
//        var vectors = Arrays.stream(domainIds).mapToObj(dToSMap::get)
//                .filter(Objects::nonNull)
//                .filter(vec -> vec.cardinalityExceeds(10))
//                .toArray(AndCardIntSet[]::new);
//        Set<Integer> domainIdsSet = new HashSet<>(Arrays.stream(domainIds).boxed().toList());
//
//        if (vectors.length != domainIds.length)
//            return;
//
//        List<DomainSimilarity> similarities = dToSMap.entrySet().parallelStream()
//                .filter(e -> !domainIdsSet.contains(e.getKey()) && indexed.contains(e.getKey()))
//                .flatMap(entry -> {
//
//                double similarity = 0.;
//                for (var vector : vectors) {
//                    similarity += cosineSimilarity(vector, entry.getValue());
//                }
//
//                if (similarity > 0.1 * vectors.length) {
//                    double recalculated = 0;
//                    for (var vector : vectors) {
//                        recalculated += expensiveCosineSimilarity(vector, entry.getValue());
//                    }
//                    if (recalculated > 0.1 * vectors.length) {
//                        return Stream.of(new DomainSimilarity(entry.getKey(), recalculated));
//                    }
//                }
//                return Stream.empty();
//        }).sorted(Comparator.comparing(DomainSimilarity::value))
//                .toList();
//
//        andThen.accept(new DomainSimilarities(domainIds[0], similarities));
//    }


    public static void main(String[] args) throws SQLException {
        DatabaseModule dm = new DatabaseModule();

        var main = new EdgeDomainLinkConsineSimilarityMain(dm.provideConnection());
        if (args.length == 0) {
            main.loadAll();
        }
        else {
            main.tryDomains(args);
        }
    }

}
