package nu.marginalia.util.ranking;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import it.unimi.dsi.fastutil.ints.IntComparator;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklistImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.function.IntToDoubleFunction;
import java.util.stream.IntStream;
import it.unimi.dsi.fastutil.ints.IntArrays;

public abstract class RankingAlgorithm {
    protected final TIntObjectHashMap<RankingDomainData> domainsById = new TIntObjectHashMap<>();
    protected final TIntIntHashMap domainIndexToId = new TIntIntHashMap();
    protected final TIntIntHashMap domainIdToIndex = new TIntIntHashMap();

    protected TIntArrayList[] linkDataSrc2Dest;
    protected TIntArrayList[] linkDataDest2Src;

    public final Set<String> originDomains = new HashSet<>();
    public final Set<Integer> originDomainIds = new HashSet<>();

    private int maxKnownUrls = Integer.MAX_VALUE;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final RankingDomainFetcher domains;

    public RankingAlgorithm(RankingDomainFetcher domains, String... origins) {
        this.domains = domains;

        originDomains.addAll(Arrays.asList(origins));

        domains.getDomains(domainData -> {
            int id = domainData.id;

            domainsById.put(id, domainData);

            domainIndexToId.put(domainIndexToId.size(), id);
            domainIdToIndex.put(id, domainIdToIndex.size());
        });

        linkDataSrc2Dest = new TIntArrayList[domainIndexToId.size()];
        linkDataDest2Src = new TIntArrayList[domainIndexToId.size()];

        domains.eachDomainLink((src, dst) -> {
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

        for (var namePattern : this.originDomains) {
            domains.domainsByPattern(namePattern, i -> {
                int ival = domainIdToIndex.get(i);
                if (ival != domainIdToIndex.getNoEntryValue() || domainIndexToId.get(0) == i) {
                    originDomainIds.add(ival);
                }
                else {
                    logger.debug("No value for {}", i);
                }
            });
        }
        logger.info("Origin Domains: {}", originDomainIds.size());
    }

    public void addPeripheralNodes() {

        int newNodesIdxCutoff = domainIdToIndex.size();

        logger.info("Inserting peripheral nodes");

        domains.getPeripheralDomains(domainData -> {
            int id = domainData.id;

            if (domainsById.put(id, domainData) == null) { // true if id was not already present
                domainIndexToId.put(domainIndexToId.size(), id);
                domainIdToIndex.put(id, domainIdToIndex.size());
            }
        });

        linkDataSrc2Dest = Arrays.copyOf(linkDataSrc2Dest, domainIndexToId.size());
        linkDataDest2Src = Arrays.copyOf(linkDataDest2Src, domainIndexToId.size());

        domains.eachDomainLink((src, dst) -> {
            if (src == dst) return;

            if (domainsById.contains(src) && domainsById.contains(dst)) {
                int srcIdx = domainIdToIndex.get(src);
                int dstIdx = domainIdToIndex.get(domainsById.get(dst).resolveAlias());

                // This looks like a bug, but it improves the results
                if (srcIdx < newNodesIdxCutoff || dstIdx < newNodesIdxCutoff)
                    return;

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

    public TIntList pageRankWithPeripheralNodes(int resultCount) {
        RankVector rank = new RankVector(1.d / domainsById.size());

        int iter_max = 100;

        for (int i = 0; i < iter_max; i++) {
            if (i == iter_max-1) {
                addPeripheralNodes();
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

    public boolean includeInRanking(RankingDomainData data) {
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

}
