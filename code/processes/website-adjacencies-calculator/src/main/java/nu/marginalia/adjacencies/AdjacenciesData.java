package nu.marginalia.adjacencies;

import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.TIntHashSet;
import org.roaringbitmap.RoaringBitmap;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AdjacenciesData {
    TIntList idsList = new TIntArrayList(100_000);
    ArrayList<SparseBitVector> itemsList = new ArrayList<>(100_000);

    TIntObjectHashMap<SparseBitVector> dToSMap = new TIntObjectHashMap<>(100_000);
    TIntObjectHashMap<RoaringBitmap> sToDMap = new TIntObjectHashMap<>(100_000);

    RoaringBitmap indexed = new RoaringBitmap();

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

    public AdjacenciesData(HikariDataSource dataSource, DomainAliases aliases) throws SQLException {

        Map<Integer, RoaringBitmap> tmpMapDtoS = new HashMap<>(100_000);
        try (
                var conn = dataSource.getConnection();
                var indexedStmt = conn.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE INDEXED>0");
                var linksStmt = conn.prepareStatement("SELECT SOURCE_DOMAIN_ID, DEST_DOMAIN_ID FROM EC_DOMAIN_LINK")) {
            ResultSet rsp;

            indexedStmt.setFetchSize(10_000);
            rsp = indexedStmt.executeQuery();
            while (rsp.next()) {
                indexed.add(rsp.getInt(1));
            }

            linksStmt.setFetchSize(10_000);
            rsp = linksStmt.executeQuery();
            while (rsp.next()) {
                int source = aliases.deAlias(rsp.getInt(1));
                int dest = aliases.deAlias(rsp.getInt(2));

                tmpMapDtoS.computeIfAbsent(dest, this::createBitmapWithSelf).add(source);


                RoaringBitmap sToDEntry = sToDMap.get(source);
                if (sToDEntry == null) {
                    sToDEntry = new RoaringBitmap();
                    sToDMap.put(source, sToDEntry);
                    sToDEntry.add(source);
                }
                sToDEntry.add(dest);
            }
        }

        tmpMapDtoS.entrySet().stream()
                .filter(e -> isEligible(e.getValue()))
                .forEach(e -> {
                    var val = SparseBitVector.of(e.getValue());
                    idsList.add(e.getKey());
                    itemsList.add(val);
                    dToSMap.put(e.getKey(), val);
                });

    }

    private boolean isEligible(RoaringBitmap value) {
//        return true;
        int cardinality = value.getCardinality();

        return cardinality < 10000;
    }


    public RoaringBitmap createBitmapWithSelf(int val) {
        var bm = new RoaringBitmap();
        bm.add(val);
        return bm;
    }

    public boolean isIndexedDomain(int domainId) {
        return indexed.contains(domainId);
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
