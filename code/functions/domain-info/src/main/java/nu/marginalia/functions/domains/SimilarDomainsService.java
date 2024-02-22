package nu.marginalia.functions.domains;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import nu.marginalia.api.domains.*;
import nu.marginalia.api.domains.model.SimilarDomain;
import nu.marginalia.api.indexdomainlinks.AggregateDomainLinksClient;
import nu.marginalia.model.EdgeDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class SimilarDomainsService {

    private static final Logger logger = LoggerFactory.getLogger(SimilarDomainsService.class);
    private final HikariDataSource dataSource;
    private final AggregateDomainLinksClient domainLinksClient;

    private volatile TIntIntHashMap domainIdToIdx = new TIntIntHashMap(100_000);
    private volatile int[] domainIdxToId;

    public volatile TIntDoubleHashMap[] relatedDomains;
    public volatile TIntList[] domainNeighbors = null;
    public volatile BitSet screenshotDomains = null;
    public volatile BitSet activeDomains = null;
    public volatile BitSet indexedDomains = null;
    public volatile double[] domainRanks = null;
    public volatile String[] domainNames = null;

    volatile boolean isReady = false;

    @Inject
    public SimilarDomainsService(HikariDataSource dataSource, AggregateDomainLinksClient domainLinksClient) {
        this.dataSource = dataSource;
        this.domainLinksClient = domainLinksClient;

        Executors.newSingleThreadExecutor().submit(this::init);
    }

    private void init() {

        logger.info("Loading similar domains data... ");
        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {

                stmt.setFetchSize(1000);
                ResultSet rs;

                rs = stmt.executeQuery("SELECT ID FROM EC_DOMAIN");
                while (rs.next()) {
                    int id = rs.getInt(1);
                    domainIdToIdx.put(id, domainIdToIdx.size());
                }
                domainIdxToId = new int[domainIdToIdx.size()];
                domainIdToIdx.forEachEntry((id, idx) -> {
                    domainIdxToId[idx] = id;
                    return true;
                });
                domainRanks = new double[domainIdToIdx.size()];
                domainNames = new String[domainIdToIdx.size()];
                domainNeighbors = new TIntList[domainIdToIdx.size()];
                screenshotDomains = new BitSet(domainIdToIdx.size());
                activeDomains = new BitSet(domainIdToIdx.size());
                indexedDomains = new BitSet(domainIdToIdx.size());
                relatedDomains = new TIntDoubleHashMap[domainIdToIdx.size()];

                logger.info("Loaded {} domain IDs", domainIdToIdx.size());

                rs = stmt.executeQuery("""
                    SELECT DOMAIN_ID, NEIGHBOR_ID, RELATEDNESS FROM EC_DOMAIN_NEIGHBORS_2
                    """);

                while (rs.next()) {
                    int did = rs.getInt(1);
                    int nid = rs.getInt(2);

                    int didx = domainIdToIdx.get(did);
                    int nidx = domainIdToIdx.get(nid);

                    int lowerIndex = Math.min(didx, nidx);
                    int higherIndex = Math.max(didx, nidx);

                    if (relatedDomains[lowerIndex] == null)
                        relatedDomains[lowerIndex] = new TIntDoubleHashMap(32);
                    relatedDomains[lowerIndex].put(higherIndex, Math.round(100 * rs.getDouble(3)));

                    if (domainNeighbors[didx] == null)
                        domainNeighbors[didx] = new TIntArrayList(32);
                    if (domainNeighbors[nidx] == null)
                        domainNeighbors[nidx] = new TIntArrayList(32);

                    domainNeighbors[didx].add(nidx);
                    domainNeighbors[nidx].add(didx);
                }

                logger.info("Loaded {} related domains", relatedDomains.length);


                rs = stmt.executeQuery("""
                    SELECT EC_DOMAIN.ID,
                           RANK,
                           STATE='ACTIVE' AS ACTIVE,
                           NODE_AFFINITY > 0 AS INDEXED,
                           EC_DOMAIN.DOMAIN_NAME AS DOMAIN_NAME
                    FROM EC_DOMAIN
                    """);

                while (rs.next()) {
                    final int id = rs.getInt("ID");
                    final int idx = domainIdToIdx.get(id);

                    domainRanks[idx] = Math.round(100 * (1. - rs.getDouble("RANK")));
                    domainNames[idx] = rs.getString("DOMAIN_NAME");

                    if (rs.getBoolean("INDEXED"))
                        indexedDomains.set(idx);

                    if (rs.getBoolean("ACTIVE"))
                        activeDomains.set(idx);
                }


                rs = stmt.executeQuery("""
                    SELECT EC_DOMAIN.ID
                    FROM EC_DOMAIN INNER JOIN DATA_DOMAIN_SCREENSHOT AS SCREENSHOT ON EC_DOMAIN.DOMAIN_NAME = SCREENSHOT.DOMAIN_NAME
                    """);

                while (rs.next()) {
                    final int id = rs.getInt(1);
                    final int idx = domainIdToIdx.get(id);

                    screenshotDomains.set(idx);
                }

                logger.info("Loaded {} domains", domainRanks.length);
                isReady = true;
            }
        }
        catch (SQLException throwables) {
            logger.warn("Failed to get domain neighbors for domain", throwables);
        }
    }

    public boolean isReady() {
        return isReady;
    }

    private double getRelatedness(int a, int b) {
        int lowerIndex = Math.min(domainIdToIdx.get(a), domainIdToIdx.get(b));
        int higherIndex = Math.max(domainIdToIdx.get(a), domainIdToIdx.get(b));

        if (relatedDomains[lowerIndex] == null)
            return 0;

        return relatedDomains[lowerIndex].get(higherIndex);
    }


    public List<RpcSimilarDomain> getSimilarDomains(int domainId, int count) {
        int domainIdx = domainIdToIdx.get(domainId);

        TIntList allIdsList = domainNeighbors[domainIdx];
        if (allIdsList == null)
            return List.of();
        TIntList allIds = new TIntArrayList(new TIntHashSet(allIdsList));

        TIntSet linkingIdsDtoS = getLinkingIdsDToS(domainIdx);
        TIntSet linkingIdsStoD = getLinkingIdsSToD(domainIdx);

        int[] idsArray = new int[allIds.size()];
        int[] idxArray = new int[idsArray.length];

        for (int i = 0; i < idsArray.length; i++) {
            idxArray[i] = allIds.get(i);
            idsArray[i] = domainIdxToId[allIds.get(i)];
        }

        double[] relatednessArray = new double[idsArray.length];
        for (int i = 0; i < idsArray.length; i++) {
            relatednessArray[i] = getRelatedness(domainId, idsArray[i]);
        }

        int[] resultIds = IntStream.range(0, idsArray.length)
                .boxed()
                .sorted((id1, id2) -> {
                    int diff = Double.compare(relatednessArray[id1], relatednessArray[id2]);
                    if (diff != 0)
                        return -diff;
                    return Integer.compare(idsArray[id1], idsArray[id2]);
                })
                .mapToInt(idx -> idxArray[idx])
                .limit(count)
                .toArray();

        List<RpcSimilarDomain> domains = new ArrayList<>();

        for (int idx : resultIds) {
            int id = domainIdxToId[idx];

            if (domainNames[idx].length() > 32)
                continue;

            var linkType = SimilarDomain.LinkType.find(
                    linkingIdsStoD.contains(idx),
                    linkingIdsDtoS.contains(idx)
            );

            domains.add(RpcSimilarDomain.newBuilder()
                    .setDomainId(id)
                    .setUrl(new EdgeDomain(domainNames[idx]).toRootUrl().toString())
                    .setRelatedness(getRelatedness(domainId, id))
                    .setRank(domainRanks[idx])
                    .setIndexed(indexedDomains.get(idx))
                    .setActive(activeDomains.get(idx))
                    .setScreenshot(screenshotDomains.get(idx))
                    .setLinkType(RpcSimilarDomain.LINK_TYPE.valueOf(linkType.name()))
                    .build());

        }

        domains.removeIf(this::shouldRemove);

        return domains;
    }

    private boolean shouldRemove(RpcSimilarDomain domainResult) {
        // Remove domains that have a relatively high likelihood of being dead links
        // or not very interesting
        if (!(domainResult.getIndexed() && domainResult.getActive())
            && domainResult.getRelatedness() <= 50)
        {
            return true;
        }

        // Remove domains that are not very similar if there is no mutual link
        if (domainResult.getLinkType() == RpcSimilarDomain.LINK_TYPE.NONE
         && domainResult.getRelatedness() <= 25)
            return true;

        return false;
    }

    private TIntSet getLinkingIdsDToS(int domainIdx) {
        var items = new TIntHashSet();

        for (int id : domainLinksClient.getLinksFromDomain(domainIdxToId[domainIdx])) {
            items.add(domainIdToIdx.get(id));
        }

        return items;
    }

    private TIntSet getLinkingIdsSToD(int domainIdx) {
        var items = new TIntHashSet();

        for (int id : domainLinksClient.getLinksToDomain(domainIdxToId[domainIdx])) {
            items.add(domainIdToIdx.get(id));
        }

        return items;
    }

    public List<RpcSimilarDomain> getLinkingDomains(int domainId, int count) {
        int domainIdx = domainIdToIdx.get(domainId);

        TIntSet linkingIdsDtoS = getLinkingIdsDToS(domainIdx);
        TIntSet linkingIdsStoD = getLinkingIdsSToD(domainIdx);

        TIntSet allIdx = new TIntHashSet(linkingIdsDtoS.size() + linkingIdsStoD.size());
        allIdx.addAll(linkingIdsDtoS);
        allIdx.addAll(linkingIdsStoD);

        int[] idxArray = allIdx.toArray();
        int[] idsArray = new int[idxArray.length];
        for (int i = 0; i < idsArray.length; i++) {
            idsArray[i] = domainIdxToId[idxArray[i]];
        }

        double[] ranksArray = new double[idsArray.length];
        for (int i = 0; i < idxArray.length; i++) {
            ranksArray[i] = this.domainRanks[idxArray[i]];
        }
        double[] relatednessArray = new double[idsArray.length];
        for (int i = 0; i < idsArray.length; i++) {
            relatednessArray[i] = getRelatedness(domainId, idsArray[i]);
        }

        int[] linkinessArray = new int[idxArray.length];
        for (int i = 0; i < idxArray.length; i++) {
            linkinessArray[i] = (linkingIdsDtoS.contains(idxArray[i]) ? 1 : 0) + (linkingIdsStoD.contains(idxArray[i]) ? 1 : 0);
        }

        int[] resultIds = IntStream.range(0, idsArray.length)
                .boxed()
                .sorted((id1, id2) -> {
                    int diff = Double.compare(ranksArray[id1], ranksArray[id2]);
                    if (diff != 0)
                        return -diff;
                    diff = Double.compare(relatednessArray[id1], relatednessArray[id2]);
                    if (diff != 0)
                        return -diff;
                    diff = Integer.compare(linkinessArray[id1], linkinessArray[id2]);
                    if (diff != 0)
                        return -diff;
                    return Integer.compare(idsArray[id1], idsArray[id2]);
                })
                .mapToInt(idx -> idsArray[idx])
                .limit(count)
                .toArray();

        List<RpcSimilarDomain> domains = new ArrayList<>();
        for (int id : resultIds) {
            int idx = domainIdToIdx.get(id);

            if (domainNames[idx].length() > 32)
                continue;

            var linkType = SimilarDomain.LinkType.find(
                    linkingIdsStoD.contains(idx),
                    linkingIdsDtoS.contains(idx)
            );

            domains.add(RpcSimilarDomain.newBuilder()
                            .setDomainId(id)
                            .setUrl(new EdgeDomain(domainNames[idx]).toRootUrl().toString())
                            .setRelatedness(getRelatedness(domainId, id))
                            .setRank(domainRanks[idx])
                            .setIndexed(indexedDomains.get(idx))
                            .setActive(activeDomains.get(idx))
                            .setScreenshot(screenshotDomains.get(idx))
                            .setLinkType(RpcSimilarDomain.LINK_TYPE.valueOf(linkType.name()))
                    .build());

        }

        domains.removeIf(this::shouldRemove);

        return domains;
    }

}