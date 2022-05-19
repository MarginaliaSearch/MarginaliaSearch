package nu.marginalia.wmsa.edge.index.service.util.ranking.old;


import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.TIntHashSet;
import lombok.AllArgsConstructor;
import lombok.Data;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.function.IntToDoubleFunction;

public class StandardPageRank {

    private final TIntObjectHashMap<DomainData> domains = new TIntObjectHashMap<>();
    private final TIntObjectHashMap<TIntArrayList> linkData = new TIntObjectHashMap<>();
    private final TIntObjectHashMap<TIntArrayList> reverseLinkData = new TIntObjectHashMap<>();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public Set<String> originDomains = new HashSet();
    public Set<Integer> originDomainIds = new HashSet<>();

    public StandardPageRank(IntToDoubleFunction weight, String... seedDomains) throws IOException {
        originDomains.addAll(Arrays.asList(seedDomains));
        loadDataFromFile();

        int[] ids = pageRank(weight, 1000).toArray();
        Arrays.stream(ids).mapToObj(domains::get).map(data ->
            String.format("%3d %2.2f %s", Optional.ofNullable(reverseLinkData.get(data.id)).map(TIntArrayList::size).orElse(0), data.quality, data.name)
        ).forEach(System.out::println);
    }

    public String domainNameFromId(int id) {
        return domains.get(id).name;
    }

    public StandardPageRank(HikariDataSource dataSource, String... origins) throws IOException {
        originDomains.addAll(Arrays.asList(origins));

        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.prepareStatement("SELECT ID,INDEXED,STATE,URL_PART FROM EC_DOMAIN WHERE INDEXED>1 AND STATE>=0 AND QUALITY>=-10")) {
                stmt.setFetchSize(10000);
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    domains.put(rsp.getInt(1), new DomainData(rsp.getInt(1), rsp.getString(4), rsp.getInt(2), rsp.getInt(3), 0));
                }
            }
            try (var stmt = conn.prepareStatement("SELECT SOURCE_DOMAIN_ID, DEST_DOMAIN_ID FROM EC_DOMAIN_LINK")) {
                stmt.setFetchSize(10000);

                var rsp = stmt.executeQuery();

                while (rsp.next()) {
                    int src = rsp.getInt(1);
                    int dst = rsp.getInt(2);

                    if (domains.contains(src) && domains.contains(dst) && domains.get(src).quality >= -5) {
                        if (!linkData.contains(src)) {
                            linkData.put(src, new TIntArrayList());
                        }
                        linkData.get(src).add(dst);

                        if (!reverseLinkData.contains(dst)) {
                            reverseLinkData.put(dst, new TIntArrayList());
                        }
                        reverseLinkData.get(dst).add(src);
                    }
                }
            }

            try (var stmt = conn.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE URL_PART=?")) {
                for (var seed : this.originDomains) {
                    stmt.setString(1, seed);
                    var rsp = stmt.executeQuery();
                    if (rsp.next()) {
                        originDomainIds.add(rsp.getInt(1));
                    }
                }
            }

        } catch (SQLException throwables) {
            logger.error("SQL error", throwables);
        }

    }

    public int size() {
        return domains.size();
    }

    public TIntList pageRank(IntToDoubleFunction weight, int resultCount) {
        RankVector rank = new RankVector(1.d / domains.size());

        int iter_max = 100;
        for (int i = 0; i < iter_max; i++) {
            RankVector newRank = createNewRankVector(rank);

            double oldNorm = rank.norm();
            double newNorm = newRank.norm();
            double dNorm = oldNorm - newNorm;
            if (i < iter_max-1) {
                originDomainIds.forEach(id -> newRank.increment(id, dNorm/originDomainIds.size()));
                newRank.incrementAll(0.14*dNorm/rank.size());
            }
            logger.debug("{} {} {}", dNorm, newNorm, rank.norm(newRank));
            rank = newRank;
        }


        return rank.getRanking(weight, resultCount);
    }

    @NotNull
    private RankVector createNewRankVector(RankVector rank) {

        final TIntArrayList empty = new TIntArrayList();

        double rankNorm = rank.norm();
        RankVector newRank = new RankVector(0);

        for (DomainData domain : domains.valueCollection()) {

            var links = Optional.ofNullable(reverseLinkData.get(domain.id)).orElse(empty);
            double newRankValue = 0;
            if (links.size() > 0) {
                for (int linkedDomain : links.toArray()) {
                    newRankValue += rank.get(linkedDomain) / linkData.get(linkedDomain).size();
                }
            }

            newRank.set(domain.id,  0.85 * newRankValue);
        }
        return newRank;
    }

    private void loadDataFromFile() throws IOException {

        try (var str = Files.lines(Path.of("/home/vlofgren/Work/data-domains.txt"))) {
            str.map(DomainData::new)
                    .filter(domain -> domain.indexed>1)
                    .filter(domain -> domain.quality>=0.1)
                    .peek(domain -> {
                        if (originDomains.contains(domain.name)) {
                            originDomainIds.add(domain.id);
                        }
                    })
                    .forEach(data -> domains.put(data.id, data));
        }

        try (var str = Files.lines(Path.of("/home/vlofgren/Work/data-links.txt"))) {
            str.map(s->s.split("\\s+")).forEach(bits -> {

                int src = Integer.parseInt(bits[0]);
                int dst = Integer.parseInt(bits[1]);

                if (domains.contains(src) && domains.contains(dst) && domains.get(src).quality >= -5) {
                    if (!linkData.contains(src)) {
                        linkData.put(src, new TIntArrayList());
                    }
                    linkData.get(src).add(dst);

                    if (!reverseLinkData.contains(dst)) {
                        reverseLinkData.put(dst, new TIntArrayList());
                    }
                    reverseLinkData.get(dst).add(src);
                }
            });
        }

        TIntHashSet deadEnds = new TIntHashSet(domains.size());
    }

    private class RankVector {
        private final TIntDoubleHashMap rank;
        private final double defaultValue;
        public RankVector(double defaultValue) {
            rank = new TIntDoubleHashMap(domains.size(), 0.75f, -1, defaultValue);
            this.defaultValue = defaultValue;
        }

        public void set(int id, double value) {
            rank.put(id, value);
        }

        public void increment(int id, double value) {
            rank.adjustOrPutValue(id, value, value);
        }

        public double get(int id) {
            return rank.get(id);
        }

        public double norm() {
            if (rank.isEmpty()) {
                return defaultValue * domains.size();
            }
            return Arrays.stream(rank.values()).map(Math::abs).sum();
        }

        public double norm(RankVector other) {
            return Arrays.stream(rank.keys()).mapToDouble(k -> Math.abs(rank.get(k) - other.get(k))).sum();
        }

        public TIntList getRanking(IntToDoubleFunction other, int numResults) {
            TIntArrayList list = new TIntArrayList(numResults);

            Comparator<DomainData> comparator = Comparator.comparing(e -> Math.sqrt(other.applyAsDouble(e.id) * rank.get(e.id)));

            domains.valueCollection().stream()
                    .sorted(comparator.reversed())
                    .map(DomainData::getId)
                    .limit(numResults)
                    .forEach(list::add);

            return list;
        }

        public TIntList getRanking2(int numResults) {
            TIntArrayList list = new TIntArrayList(numResults);

            Comparator<DomainData> comparator = Comparator.comparing(e -> rank.get(e.id));

            domains.valueCollection().stream()
                    .sorted(comparator.reversed())
                    .map(DomainData::getId)
                    .limit(numResults)
                    .forEach(list::add);

            return list;
        }

        public void incrementAll(double v) {
            rank.transformValues(oldv -> oldv + v);
        }

        int size() {
            return domains.size();
        }
    }
    @Data @AllArgsConstructor
    static class DomainData {

        public DomainData(String str) {
            String[] parts = str.split("\\s+");

            id = Integer.parseInt(parts[0]);
            name = parts[2];
            indexed = Integer.parseInt(parts[3]);
            state = Integer.parseInt(parts[4]);
            quality = Double.parseDouble(parts[5]);
        }
        public final int id;
        public final String name;
        public final int indexed;
        public final int state;
        public double quality;
    }

}
