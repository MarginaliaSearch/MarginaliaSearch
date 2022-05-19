package nu.marginalia.util.ranking.tool;


import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.TIntHashSet;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntComparator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import nu.marginalia.util.ranking.RankingAlgorithm;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklistImpl;
import org.jetbrains.annotations.NotNull;
import org.mariadb.jdbc.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.IntToDoubleFunction;
import java.util.stream.IntStream;

public class PerusePageRankV2 {

    final TIntObjectHashMap<DomainData> domainsById = new TIntObjectHashMap<>();
    final TIntIntHashMap domainIndexToId = new TIntIntHashMap();
    final TIntIntHashMap domainIdToIndex = new TIntIntHashMap();

    private final TIntHashSet spamDomains;
    private final HikariDataSource dataSource;

    TIntArrayList[] linkDataSrc2Dest;
    TIntArrayList[] linkDataDest2Src;

    private static boolean getNames = true;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    static LinkedBlockingQueue<LinkAdjacencies> uploadQueue = new LinkedBlockingQueue<>(10);
    volatile static boolean running = true;

    public int indexMax() {
        return domainIndexToId.size();
    }

    public int getDomainId(int idx) {
        return domainIndexToId.get(idx);
    }

    @SneakyThrows
    public static void main(String... args) throws IOException {
        org.mariadb.jdbc.Driver driver = new Driver();
        var conn = new DatabaseModule().provideConnection();
        var rank = new PerusePageRankV2(conn);

        long start = System.currentTimeMillis();
        var uploader = new Thread(() -> uploadThread(conn));
        uploader.start();

        IntStream.range(0, rank.indexMax()).parallel().forEach(i -> {
            int[] ids = rank.pageRank(i, 25).toArray();
            try {
                uploadQueue.put(new LinkAdjacencies(rank.getDomainId(i), ids));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        long end = System.currentTimeMillis();
        running = false;
        uploader.join();
        System.out.printf("%2.2f", (end - start)/1000.0);
    }

    @AllArgsConstructor
    static class LinkAdjacencies {
        public final int id;
        public final int[] neighbors;
    };

    public static void uploadThread(HikariDataSource dataSource) {
        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.prepareStatement("INSERT INTO EC_DOMAIN_NEIGHBORS(DOMAIN_ID, NEIGHBOR_ID, ADJ_IDX) VALUES (?,?,?) ON DUPLICATE KEY UPDATE NEIGHBOR_ID=VALUES(NEIGHBOR_ID)")) {
                while (running || (!running && !uploadQueue.isEmpty())) {
                    var job = uploadQueue.take();
                    for (int i = 0; i < job.neighbors.length; i++) {
                        stmt.setInt(1, job.id);
                        stmt.setInt(2, job.neighbors[i]);
                        stmt.setInt(3, i);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
            }
        } catch (SQLException | InterruptedException throwables) {
            throwables.printStackTrace();
        }
    }

    public PerusePageRankV2(HikariDataSource dataSource) throws IOException {
        var blacklist = new EdgeDomainBlacklistImpl(dataSource);
        spamDomains = blacklist.getSpamDomains();
        this.dataSource = dataSource;

        try (var conn = dataSource.getConnection()) {
            String s;
            if (getNames) {
                s = "SELECT EC_DOMAIN.ID,URL_PART,DOMAIN_ALIAS FROM EC_DOMAIN INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID WHERE ((INDEXED>1 AND STATE >= 0) OR (INDEXED=1 AND VISITED_URLS=KNOWN_URLS AND GOOD_URLS>0)) AND QUALITY_RAW>=-20 GROUP BY EC_DOMAIN.ID";
            }
            else {
                s = "SELECT EC_DOMAIN.ID,\"\",DOMAIN_ALIAS FROM EC_DOMAIN ON EC_DOMAIN.ID=DOMAIN_METADATA.ID WHERE ((INDEXED>1 AND STATE >= 0) OR (INDEXED=1 AND VISITED_URLS=KNOWN_URLS AND GOOD_URLS>0)) AND QUALITY_RAW>=-20 GROUP BY EC_DOMAIN.ID";
            }
            try (var stmt = conn.prepareStatement(s)) {
                stmt.setFetchSize(10000);
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    int id = rsp.getInt(1);
                    if (!spamDomains.contains(id)) {

                        domainsById.put(id, new DomainData(id, rsp.getString(2),  rsp.getInt(3), false));

                        domainIndexToId.put(domainIndexToId.size(), id);
                        domainIdToIndex.put(id, domainIdToIndex.size());
                    }
                }
            }


            linkDataSrc2Dest = new TIntArrayList[domainIndexToId.size()];
            linkDataDest2Src = new TIntArrayList[domainIndexToId.size()];

            try (var stmt = conn.prepareStatement("SELECT SOURCE_DOMAIN_ID, DEST_DOMAIN_ID FROM EC_DOMAIN_LINK")) {
                stmt.setFetchSize(10000);

                var rsp = stmt.executeQuery();

                while (rsp.next()) {
                    int src = rsp.getInt(1);
                    int dst = rsp.getInt(2);

                    if (src == dst) continue;

                    if (domainsById.contains(src) && domainsById.contains(dst)) {

                        int srcIdx = domainIdToIndex.get(src);
                        int dstIdx = domainIdToIndex.get(domainsById.get(dst).resolveAlias());

                        if (linkDataSrc2Dest[srcIdx] == null) {
                            linkDataSrc2Dest[srcIdx] = new TIntArrayList();
                        }
                        linkDataSrc2Dest[srcIdx].add(dstIdx);

                        if (linkDataDest2Src[dstIdx] == null) {
                            linkDataDest2Src[dstIdx] = new TIntArrayList();
                        }
                        linkDataDest2Src[dstIdx].add(srcIdx);
                    }
                }
            }

        } catch (SQLException throwables) {
            logger.error("SQL error", throwables);
        }

    }

    public TIntList pageRank(int origin, int resultCount) {
        RankVector rank = new RankVector(1.d / domainsById.size());

        int iter_max = 10;
        for (int i = 0; i < iter_max; i++) {
            RankVector newRank = createNewRankVector(rank);

            double oldNorm = rank.norm();
            double newNorm = newRank.norm();
            double dNorm = oldNorm - newNorm ;

            newRank.increment(origin, dNorm/oldNorm);

            rank = newRank;
        }

        rank.increment(origin, -1);

        return rank.getRanking(resultCount);
    }

    @NotNull
    private RankVector createNewRankVector(RankVector rank) {

        double rankNorm = rank.norm();
        RankVector newRank = new RankVector(0);

        for (int domainId = 0; domainId < domainIndexToId.size(); domainId++) {

            var links = linkDataSrc2Dest[domainId];
            double newRankValue = 0;

            if (links != null && links.size() > 0) {


                for (int j = 0; j < links.size(); j++) {
                    var revLinks = linkDataDest2Src[links.getQuick(j)];
                    newRankValue += rank.get(links.getQuick(j)) / revLinks.size();
                }
            }

            newRank.set(domainId, 0.85*newRankValue/rankNorm);
        }

        return newRank;
    }

    public class RankVector {
        private final double[] rank;
        public RankVector(double defaultValue) {
            rank = new double[domainIndexToId.size()];
            if (defaultValue != 0.) {
                Arrays.fill(rank, defaultValue);
            }
        }

        public void set(int id, double value) {
            rank[id] = value;
        }

        public void increment(int id, double value) {
            rank[id] += value;
        }

        public double get(int id) {
            if (id >= rank.length) return 0.;

            return rank[id];
        }

        public double norm() {
            double v = 0.;
            for (int i = 0; i < rank.length; i++) {
                if (rank[i] > 0) { v+=rank[i]; }
                else { v -= rank[i]; }
            }
            return v;
        }

        public double norm(RankingAlgorithm.RankVector other) {
            double v = 0.;
            for (int i = 0; i < rank.length; i++) {
                double dv = rank[i] - other.get(i);

                if (dv > 0) { v+=dv; }
                else { v -= dv; }
            }
            return v;
        }

        public TIntList getRanking(IntToDoubleFunction other, int numResults) {
            TIntArrayList list = new TIntArrayList(numResults);

            Comparator<Integer> comparator = Comparator.comparing(i -> Math.sqrt(other.applyAsDouble(domainIdToIndex.get(i)) * rank[i]));

            IntStream.range(0, rank.length)
                    .boxed()
                    .sorted(comparator.reversed())
                    .map(domainIndexToId::get)
                    .limit(numResults)
                    .forEach(list::add);

            return list;
        }

        public TIntList getRanking(int numResults) {
            if (numResults < 0) {
                numResults = domainIdToIndex.size();
            }
            TIntArrayList list = new TIntArrayList(numResults);

            int[] nodes = new int[rank.length];
            Arrays.setAll(nodes, i->i);
            IntComparator comp = (i,j) -> (int) Math.signum(rank[j] - rank[i]);
            IntArrays.quickSort(nodes, comp);

            int i;

            for (i = 0; i < numResults; i++) {
                int id = domainIndexToId.get(nodes[i]);

                if (!domainsById.get(id).isAlias())
                    list.add(id);
            }

            for (; i < nodes.length && domainsById.size() < numResults; i++) {
                int id = domainIndexToId.get(nodes[i]);

                if (!domainsById.get(id).isAlias())
                    list.add(id);
            }


            return list;
        }

        public void incrementAll(double v) {
            for (int i = 0; i < rank.length; i++) {
                rank[i]+=v;
            }
        }

        int size() {
            return domainsById.size();
        }
    }

    @Data
    @AllArgsConstructor
    static class DomainData {
        public final int id;
        public final String name;
        private int alias;

        public int resolveAlias() {
            if (alias == 0) return id;
            return alias;
        }

        public boolean isAlias() {
            return alias != 0;
        }

        public boolean peripheral;
    }
}
