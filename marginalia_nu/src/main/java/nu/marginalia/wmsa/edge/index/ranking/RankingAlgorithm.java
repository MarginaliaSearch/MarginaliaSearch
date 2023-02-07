package nu.marginalia.wmsa.edge.index.ranking;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import it.unimi.dsi.fastutil.ints.IntArrays;
import nu.marginalia.wmsa.edge.index.ranking.accumulator.RankingResultAccumulator;
import nu.marginalia.wmsa.edge.index.ranking.data.RankingDomainData;
import nu.marginalia.wmsa.edge.index.ranking.data.RankingDomainFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import static java.lang.Math.min;

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

    public RankingDomainData getDomainData(int id) {
        return domainsById.get(id);
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

    public <T> T pageRank(int resultCount, Supplier<RankingResultAccumulator<T>> accumulatorP) {
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


        return rank.getRanking(resultCount, accumulatorP).get();
    }

    public <T> T pageRankWithPeripheralNodes(int resultCount, Supplier<RankingResultAccumulator<T>> accumulatorP) {
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

        return rank.getRanking(resultCount, accumulatorP).get();
    }

    abstract void adjustRankVector(RankVector vector, double dNorm, double oldNorm);

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
            for (double value : rank) {
                v += Math.abs(value);
            }
            return v;
        }

        public double norm(RankVector other) {
            double v = 0.;
            for (int i = 0; i < rank.length; i++) {
                v += Math.abs(rank[i] - other.get(i));
            }
            return v;
        }

        public <T> RankingResultAccumulator<T> getRanking(int numResults, Supplier<RankingResultAccumulator<T>> accumulatorP) {

            if (numResults < 0) {
                numResults = domainIdToIndex.size();
            }
            numResults = min(numResults, min(domainIdToIndex.size(), rank.length));

            int[] nodes = sortOrder(rank);
            var accumulator = accumulatorP.get();

            for (int i = 0; i < numResults; i++) {
                int id = domainIndexToId.get(nodes[i]);

                if (includeInRanking(domainsById.get(id)))
                    accumulator.add(id, i);
            }

            return accumulator;
        }
        private static int[] sortOrder(double[] values) {

            int[] ret = new int[values.length];
            Arrays.setAll(ret, i->i);
            IntArrays.quickSort(ret, (i,j) -> (int) Math.signum(values[j] - values[i]));

            return ret;
        }

    }

}
