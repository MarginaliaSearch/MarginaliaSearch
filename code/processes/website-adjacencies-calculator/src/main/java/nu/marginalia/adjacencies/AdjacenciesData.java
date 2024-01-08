package nu.marginalia.adjacencies;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.TIntHashSet;
import nu.marginalia.query.client.QueryClient;
import org.roaringbitmap.RoaringBitmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AdjacenciesData {
    private static final Logger logger = LoggerFactory.getLogger(AdjacenciesData.class);
    private final TIntList idsList = new TIntArrayList(100_000);
    private final ArrayList<SparseBitVector> itemsList = new ArrayList<>(100_000);

    private final TIntObjectHashMap<SparseBitVector> dToSMap = new TIntObjectHashMap<>(100_000);
    private final TIntObjectHashMap<RoaringBitmap> sToDMap = new TIntObjectHashMap<>(100_000);

    public TIntHashSet getCandidates(SparseBitVector vec) {
        TIntHashSet ret = new TIntHashSet();

        var sources = vec.backingList;
        for (int i = 0; i < sources.size(); i++) {
            var sToD = sToDMap.get(sources.getQuick(i));
            if (sToD != null) {
                ret.addAll(sToD.toArray());
            }
        }

        return ret;
    }

    public AdjacenciesData(QueryClient queryClient,
                           DomainAliases aliases) {
        logger.info("Loading adjacency data");

        Map<Integer, RoaringBitmap> tmpMapDtoS = new HashMap<>(100_000);

        int count = 0;
        var allLinks = queryClient.getAllDomainLinks();
        for (var iter = allLinks.iterator();;count++) {
            if (!iter.advance()) {
                break;
            }
            int source = aliases.deAlias(iter.source());
            int dest = aliases.deAlias(iter.dest());

            tmpMapDtoS.computeIfAbsent(dest, this::createBitmapWithSelf).add(source);
            RoaringBitmap sToDEntry = sToDMap.get(source);
            if (sToDEntry == null) {
                sToDEntry = new RoaringBitmap();
                sToDMap.put(source, sToDEntry);
                sToDEntry.add(source);
            }
            sToDEntry.add(dest);
        }
        logger.info("Links loaded: {}", count);

        tmpMapDtoS.entrySet().stream()
                .filter(e -> isEligible(e.getValue()))
                .forEach(e -> {
                    var val = SparseBitVector.of(e.getValue());
                    idsList.add(e.getKey());
                    itemsList.add(val);
                    dToSMap.put(e.getKey(), val);
                });

        logger.info("All adjacency dat loaded");
    }

    private boolean isEligible(RoaringBitmap value) {
        int cardinality = value.getCardinality();

        return cardinality < 10000;
    }


    public RoaringBitmap createBitmapWithSelf(int val) {
        var bm = new RoaringBitmap();
        bm.add(val);
        return bm;
    }

    public TIntList getIdsList() {
        return idsList;
    }

    public ArrayList<SparseBitVector> allVectors() {
        return itemsList;
    }

    SparseBitVector getVector(int id) {
        return dToSMap.get(id);
    }

    float[] getWeights() {
        float[] weights = new float[1 + idsList.max()];
        for (int i = 0; i < idsList.size(); i++) {
            weights[idsList.get(i)] = getWeight(idsList.get(i));
        }
        return weights;
    }

    float getWeight(int i) {
        var vector = dToSMap.get(i);

        if (vector == null) return 1.0f;
        return 1.0f / (float) Math.log(2+vector.getCardinality());
    }

    public int getIdFromIdx(int i) {
        return idsList.get(i);
    }

}
