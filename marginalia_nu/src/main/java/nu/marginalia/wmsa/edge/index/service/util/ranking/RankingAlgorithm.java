package nu.marginalia.wmsa.edge.index.service.util.ranking;

import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.TIntHashSet;
import it.unimi.dsi.fastutil.ints.IntComparator;
import lombok.AllArgsConstructor;
import lombok.Data;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklistImpl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.function.IntToDoubleFunction;
import java.util.stream.IntStream;
import it.unimi.dsi.fastutil.ints.IntArrays;

public abstract class RankingAlgorithm {
    final TIntObjectHashMap<DomainData> domainsById = new TIntObjectHashMap<>();
    final TIntIntHashMap domainIndexToId = new TIntIntHashMap();
    final TIntIntHashMap domainIdToIndex = new TIntIntHashMap();

    private final TIntHashSet spamDomains;
    private final HikariDataSource dataSource;

    TIntArrayList[] linkDataSrc2Dest;
    TIntArrayList[] linkDataDest2Src;

    public Set<String> originDomains = new HashSet<>();
    public Set<Integer> originDomainIds = new HashSet<>();

    private int maxKnownUrls = Integer.MAX_VALUE;

    private static boolean getNames = true;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static void main(String... args) throws IOException {
        var rpr = new BuggyReversePageRank(new DatabaseModule().provideConnection(), "wiki.xxiivv.com");
        var spr = new BuggyStandardPageRank(new DatabaseModule().provideConnection(), "memex.marginalia.nu");

        var rankVector = spr.pageRankVector();
        var norm = rankVector.norm();
        rpr.pageRank(i -> rankVector.get(i) / norm, 25).forEach(i -> {
            System.out.println(spr.domainNameFromId(i));
            return true;
        });
    }

    public String domainNameFromId(int id) {
        return domainsById.get(id).name;
    }
    public boolean isPeripheral(int id) {
        return domainsById.get(id).peripheral;
    }

    public RankingAlgorithm(HikariDataSource dataSource, String... origins) {
        this.dataSource = dataSource;
        var blacklist = new EdgeDomainBlacklistImpl(dataSource);

        spamDomains = blacklist.getSpamDomains();
        originDomains.addAll(Arrays.asList(origins));

        try (var conn = dataSource.getConnection()) {

                String s;
                if (getNames) {
                    s = "SELECT EC_DOMAIN.ID,URL_PART,DOMAIN_ALIAS,STATE,KNOWN_URLS FROM EC_DOMAIN INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID INNER JOIN EC_DOMAIN_LINK ON SOURCE_DOMAIN_ID=EC_DOMAIN.ID WHERE ((INDEXED>1 AND STATE >= 0) OR (INDEXED=1 AND VISITED_URLS=KNOWN_URLS AND GOOD_URLS>0)) AND QUALITY_RAW>=-20 AND SOURCE_DOMAIN_ID!=DEST_DOMAIN_ID GROUP BY EC_DOMAIN.ID";
                }
                else {
                    s = "SELECT EC_DOMAIN.ID,\"\",DOMAIN_ALIAS,STATE,KNOWN_URLS FROM EC_DOMAIN INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID INNER JOIN EC_DOMAIN_LINK ON SOURCE_DOMAIN_ID=EC_DOMAIN.ID WHERE ((INDEXED>1 AND STATE >= 0) OR (INDEXED=1 AND VISITED_URLS=KNOWN_URLS AND GOOD_URLS>0)) AND QUALITY_RAW>=-20 AND SOURCE_DOMAIN_ID!=DEST_DOMAIN_ID GROUP BY EC_DOMAIN.ID";
                }
                try (var stmt = conn.prepareStatement(s)) {
                    stmt.setFetchSize(10000);
                    var rsp = stmt.executeQuery();
                    while (rsp.next()) {
                        int id = rsp.getInt(1);
                        if (!spamDomains.contains(id)) {

                            domainsById.put(id, new DomainData(id, rsp.getString(2),  rsp.getInt(3), rsp.getInt(4), rsp.getInt(5), false));

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

            try (var stmt = conn.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE URL_PART LIKE ?")) {
                for (var seed : this.originDomains) {
                    stmt.setString(1, seed);
                    var rsp = stmt.executeQuery();
                    while (rsp.next()) {
                        int i = rsp.getInt(1);
                        int ival = domainIdToIndex.get(i);
                        if (ival != domainIdToIndex.getNoEntryValue() || domainIndexToId.get(0) == i) {
                            originDomainIds.add(ival);
                        }
                        else {
                            logger.debug("No value for {}", i);
                        }
                    }
                    logger.debug("{} -> {}", seed, originDomainIds.size());
                }
            }

            logger.info("Origin Domains: {}", originDomainIds.size());

        } catch (SQLException throwables) {
            logger.error("SQL error", throwables);
        }
    }

    public void addPeripheralNodes(boolean includeErrorStates) {

        int newNodesIdxCutoff = domainIdToIndex.size();

        logger.info("Inserting peripheral nodes");

        try (var conn = dataSource.getConnection()) {
            String s;
            if (getNames) {
                s = "SELECT EC_DOMAIN.ID,URL_PART,DOMAIN_ALIAS,STATE,KNOWN_URLS FROM EC_DOMAIN INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID  LEFT JOIN EC_DOMAIN_LINK ON SOURCE_DOMAIN_ID=EC_DOMAIN.ID WHERE ((INDEXED>1 AND STATE >= 0) OR (INDEXED=1 AND VISITED_URLS=KNOWN_URLS AND GOOD_URLS>0)) AND QUALITY_RAW>=-20 AND EC_DOMAIN_LINK.ID IS NULL GROUP BY EC_DOMAIN.ID";
            }
            else {
                s = "SELECT EC_DOMAIN.ID,\"\",DOMAIN_ALIAS,STATE,KNOWN_URLS FROM EC_DOMAIN INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID  LEFT JOIN EC_DOMAIN_LINK ON SOURCE_DOMAIN_ID=EC_DOMAIN.ID WHERE ((INDEXED>1 AND STATE >= 0) OR (INDEXED=1 AND VISITED_URLS=KNOWN_URLS AND GOOD_URLS>0)) AND QUALITY_RAW>=-20 AND EC_DOMAIN_LINK.ID IS NULL GROUP BY EC_DOMAIN.ID";
            }
            try (var stmt = conn.prepareStatement(s)) {
                stmt.setFetchSize(10000);
                var rsp = stmt.executeQuery();

                while (rsp.next()) {
                    int id = rsp.getInt(1);

                    if (!spamDomains.contains(id)) {
                        domainsById.put(id, new DomainData(id, rsp.getString(2), rsp.getInt(3), rsp.getInt(4), rsp.getInt(5), true));

                        domainIndexToId.put(domainIndexToId.size(), id);
                        domainIdToIndex.put(id, domainIdToIndex.size());
                    }
                }

            }

            linkDataSrc2Dest = Arrays.copyOf(linkDataSrc2Dest, domainIndexToId.size());
            linkDataDest2Src = Arrays.copyOf(linkDataDest2Src, domainIndexToId.size());

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

                        // This looks like a bug, but it improves the results
                        if (srcIdx < newNodesIdxCutoff || dstIdx < newNodesIdxCutoff)
                            continue;

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

        logger.info("Peripheral nodes inserted {} -> {}", newNodesIdxCutoff, domainIdToIndex.size());
    }

    public int size() {
        return domainsById.size();
    }


    public RankVector pageRankVector() {
        RankVector rank = new RankVector(1.d / domainsById.size());

        int iter_max = 100;
        for (int i = 0; i < iter_max; i++) {
            RankVector newRank = createNewRankVector(rank);

            double oldNorm = rank.norm();
            double newNorm = newRank.norm();
            double dNorm = oldNorm - newNorm ;
            if (i < iter_max-1) {
                adjustRankVector(newRank, dNorm, oldNorm);
            }

            rank = newRank;
        }

        return rank;
    }


    public TIntList pageRank(int resultCount) {
        RankVector rank = new RankVector(1.d / domainsById.size());

        int iter_max = 100;
        for (int i = 0; i < iter_max; i++) {
            RankVector newRank = createNewRankVector(rank);

            double oldNorm = rank.norm();
            double newNorm = newRank.norm();
            double dNorm = oldNorm - newNorm;

            if (i < iter_max-1) {
                adjustRankVector(newRank, dNorm, oldNorm);
            }

            rank = newRank;
        }


        return rank.getRanking(resultCount);
    }

    public TIntList pageRankWithPeripheralNodes(int resultCount, boolean includeErrorStates) {
        RankVector rank = new RankVector(1.d / domainsById.size());

        int iter_max = 100;

        for (int i = 0; i < iter_max; i++) {
            if (i == iter_max-1) {
                addPeripheralNodes(includeErrorStates);
            }
            RankVector newRank = createNewRankVector(rank);

            double oldNorm = rank.norm();
            double newNorm = newRank.norm();
            double dNorm = oldNorm - newNorm;

            if (i < iter_max-1) {
                adjustRankVector(newRank, dNorm, oldNorm);
            }

            rank = newRank;
        }

        logger.info("PRWPN iteration done");

        return rank.getRanking(resultCount);
    }

    abstract void adjustRankVector(RankVector vector, double dNorm, double oldNorm);

    public TIntList pageRank(IntToDoubleFunction weight, int resultCount) {
        RankVector rank = new RankVector(1.d / domainsById.size());

        int iter_max = 100;
        for (int i = 0; i < iter_max; i++) {
            RankVector newRank = createNewRankVector(rank);

            double oldNorm = rank.norm();
            double newNorm = newRank.norm();
            double dNorm = oldNorm - newNorm ;

            if (i < iter_max-1) {
                adjustRankVector(newRank, dNorm, oldNorm);
            }

            rank = newRank;
        }

        return rank.getRanking(weight, resultCount);
    }

    abstract RankVector createNewRankVector(RankVector rank);

    public boolean includeInRanking(DomainData data) {
        if (data.isAlias())
            return false;
        if (data.isSpecial())
            return false;
        if (data.isSocialMedia())
            return false;
        if (data.knownUrls > maxKnownUrls)
            return false;

        return true;
    }

    public void setMaxKnownUrls(int maxKnownUrls) {
        this.maxKnownUrls = maxKnownUrls;
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

        public double norm(RankVector other) {
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
            if (numResults >= rank.length) {
                numResults = rank.length;
            }

            TIntArrayList list = new TIntArrayList(numResults);

            int[] nodes = new int[rank.length];
            Arrays.setAll(nodes, i->i);
            IntComparator comp = (i,j) -> (int) Math.signum(rank[j] - rank[i]);
            IntArrays.quickSort(nodes, comp);

            int i;

            for (i = 0; i < numResults; i++) {
                int id = domainIndexToId.get(nodes[i]);

                if (includeInRanking(domainsById.get(id)))
                    list.add(id);
            }

            for (; i < nodes.length && domainsById.size() < numResults; i++) {
                int id = domainIndexToId.get(nodes[i]);

                if (includeInRanking(domainsById.get(id)))
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
        private int state;
        public final int knownUrls;
        public boolean peripheral;

        public int resolveAlias() {
            if (alias == 0) return id;
            return alias;
        }

        public boolean isAlias() {
            return alias != 0;
        }

        public boolean isSpecial() {
            return EdgeDomainIndexingState.SPECIAL.code == state;
        }

        public boolean isSocialMedia() {
            return EdgeDomainIndexingState.SOCIAL_MEDIA.code == state;
        }
    }

}
