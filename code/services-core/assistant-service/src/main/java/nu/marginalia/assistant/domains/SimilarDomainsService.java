package nu.marginalia.assistant.domains;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import nu.marginalia.assistant.client.model.SimilarDomain;
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

    private volatile TIntIntHashMap domainIdToIdx = new TIntIntHashMap(100_000);
    private volatile int[] domainIdxToId;

    public volatile TIntDoubleHashMap[] relatedDomains;
    public volatile TIntList[] domainNeighbors = null;
    public volatile TIntList[] linkStoD = null;
    public volatile TIntList[] linkDtoS = null;
    public volatile BitSet screenshotDomains = null;
    public volatile BitSet activeDomains = null;
    public volatile BitSet indexedDomains = null;
    public volatile double[] domainRanks = null;
    public volatile String[] domainNames = null;

    volatile boolean isReady = false;

    @Inject
    public SimilarDomainsService(HikariDataSource dataSource) {
        this.dataSource = dataSource;

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
                linkStoD = new TIntList[domainIdToIdx.size()];
                linkDtoS = new TIntList[domainIdToIdx.size()];
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
                    SELECT SOURCE_DOMAIN_ID, DEST_DOMAIN_ID FROM EC_DOMAIN_LINK
                    """);

                while (rs.next()) {
                    int source = rs.getInt(1);
                    int dest = rs.getInt(2);

                    int sourceIdx = domainIdToIdx.get(source);
                    int destIdx = domainIdToIdx.get(dest);

                    if (linkStoD[sourceIdx] == null)
                        linkStoD[sourceIdx] = new TIntArrayList(32);
                    if (linkDtoS[destIdx] == null)
                        linkDtoS[destIdx] = new TIntArrayList(32);

                    linkStoD[sourceIdx].add(destIdx);
                    linkDtoS[destIdx].add(sourceIdx);

                }
                logger.info("Loaded links...");

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
                logger.info("All done!");
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


    public List<SimilarDomain> getSimilarDomains(int domainId, int count) {
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

        List<SimilarDomain> domains = new ArrayList<>();
        for (int idx : resultIds) {
            int id = domainIdxToId[idx];

            domains.add(new SimilarDomain(
                    new EdgeDomain(domainNames[idx]).toRootUrl(),
                    id,
                    getRelatedness(domainId, id),
                    domainRanks[idx],
                    indexedDomains.get(idx),
                    activeDomains.get(idx),
                    screenshotDomains.get(idx),
                    SimilarDomain.LinkType.find(
                            linkingIdsStoD.contains(idx),
                            linkingIdsDtoS.contains(idx)
                    )
            ));
        }

        domains.removeIf(this::shouldRemove);

        return domains;
    }

    private boolean shouldRemove(SimilarDomain domainResult) {
        if (domainResult.url().domain.toString().length() > 32)
            return true;

        // Remove domains that have a relatively high likelihood of being dead links
        // or not very interesting
        if (!domainResult.indexed()
            && !domainResult.active()
            && domainResult.relatedness() > 50)
        {
            return true;
        }
        return false;
    }

    private TIntSet getLinkingIdsDToS(int domainIdx) {
        var items = linkDtoS[domainIdx];
        if (items == null)
            return new TIntHashSet();
        return new TIntHashSet(items);
    }

    private TIntSet getLinkingIdsSToD(int domainIdx) {
        var items = linkStoD[domainIdx];
        if (items == null)
            return new TIntHashSet();
        return new TIntHashSet(items);
    }

    public List<SimilarDomain> getLinkingDomains(int domainId, int count) {
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
            ranksArray[i] = domainRanks[idxArray[i]];
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

        List<SimilarDomain> domains = new ArrayList<>();
        for (int id : resultIds) {
            int idx = domainIdToIdx.get(id);

            domains.add(new SimilarDomain(
                    new EdgeDomain(domainNames[idx]).toRootUrl(),
                    id,
                    getRelatedness(domainId, id),
                    domainRanks[idx],
                    indexedDomains.get(idx),
                    activeDomains.get(idx),
                    screenshotDomains.get(idx),
                    SimilarDomain.LinkType.find(
                            linkingIdsStoD.contains(idx),
                            linkingIdsDtoS.contains(idx)
                    )
            ));
        }

        domains.removeIf(this::shouldRemove);

        return domains;
    }

}