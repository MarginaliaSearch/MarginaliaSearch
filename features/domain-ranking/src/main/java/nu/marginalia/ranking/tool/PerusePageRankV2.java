package nu.marginalia.ranking.tool;


import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntComparator;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import nu.marginalia.ranking.RankingAlgorithm;
import nu.marginalia.ranking.data.RankingDomainData;
import nu.marginalia.ranking.data.RankingDomainFetcher;
import nu.marginalia.model.dbcommon.EdgeDomainBlacklistImpl;
import nu.marginalia.service.module.DatabaseModule;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.IntStream;

public class PerusePageRankV2 {

    final TIntObjectHashMap<RankingDomainData> domainsById = new TIntObjectHashMap<>();
    final TIntIntHashMap domainIndexToId = new TIntIntHashMap();
    final TIntIntHashMap domainIdToIndex = new TIntIntHashMap();

    TIntArrayList[] linkDataSrc2Dest;
    TIntArrayList[] linkDataDest2Src;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    static final LinkedBlockingQueue<LinkAdjacencies> uploadQueue = new LinkedBlockingQueue<>(10);
    volatile static boolean running = true;

    public int indexMax() {
        return domainIndexToId.size();
    }

    public int getDomainId(int idx) {
        return domainIndexToId.get(idx);
    }

    @SneakyThrows
    public static void main(String... args) {
        var ds = new DatabaseModule().provideConnection();
        var blacklist = new EdgeDomainBlacklistImpl(ds);
        var rank = new PerusePageRankV2(new RankingDomainFetcher(ds, blacklist));

        long start = System.currentTimeMillis();
        var uploader = new Thread(() -> uploadThread(ds));
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
    }

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

    public PerusePageRankV2(RankingDomainFetcher domainFetcher) {

        domainFetcher.getDomains(domainData -> {
            int id = domainData.id;

            domainsById.put(id, domainData);

            domainIndexToId.put(domainIndexToId.size(), id);
            domainIdToIndex.put(id, domainIdToIndex.size());
        });
        domainFetcher.getPeripheralDomains(domainData -> {
            int id = domainData.id;

            domainsById.put(id, domainData);

            domainIndexToId.put(domainIndexToId.size(), id);
            domainIdToIndex.put(id, domainIdToIndex.size());
        });

        linkDataSrc2Dest = new TIntArrayList[domainIndexToId.size()];
        linkDataDest2Src = new TIntArrayList[domainIndexToId.size()];

        domainFetcher.eachDomainLink((src, dst) -> {
            if (src == dst) return;

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
        });
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
    }

}
