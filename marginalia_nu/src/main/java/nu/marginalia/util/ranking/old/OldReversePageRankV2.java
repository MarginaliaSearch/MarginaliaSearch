package nu.marginalia.util.ranking.old;


import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;

public class OldReversePageRankV2 {

    private final TIntObjectHashMap<DomainData> domains = new TIntObjectHashMap<>();
    private final TIntObjectHashMap<TIntArrayList> linkData = new TIntObjectHashMap<>();
    private final TIntObjectHashMap<TIntArrayList> reverseLinkData = new TIntObjectHashMap<>();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public final Set<String> originDomains = new HashSet<>();
    public final Set<Integer> originDomainIds = new HashSet<>();

    public static void main(String... args) throws IOException {
        new OldReversePageRankV2(
//                "wiki.xxiivv.com",
//                "stpeter.im",
//                "datagubbe.se", "midnight.pub",
//                "www.gameboomers.com",
//                "www.wild-seven.org", "iocane-powder.net", "www.doujinshi.org", "ohmydarling.org",
//                "lobste.rs",
//                "dataswamp.org", "www.ohtori.nu",
//                "lukesmith.xyz", "internetgirlfriend.club",
//                "tilde.town", "tilde.team",
//                "felix.plesoianu.ro",
//                "www.neustadt.fr",
                "memex.marginalia.nu"
        );
    }

    public OldReversePageRankV2(String... seedDomains) throws IOException {
        loadDataFromFile();

        long start = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            if (domains.contains(i)) {
                int[] ids = pageRank(10).toArray();
                System.out.printf("%d %d\n", i, ids.length);
            }
//            Arrays.stream(ids).mapToObj(domains::get).map(data ->
//                    String.format("%3d %2.2f %s", Optional.ofNullable(reverseLinkData.get(data.id)).map(TIntArrayList::size).orElse(0), data.quality, data.name)
//            ).forEach(System.out::println);
        }
        long end = System.currentTimeMillis();
        System.out.printf("%2.2f", (end - start)/1000.0);
    }

    public OldReversePageRankV2(HikariDataSource dataSource) {
        originDomains.add("memex.marginalia.nu");

        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.prepareStatement("SELECT ID,INDEXED,STATE FROM EC_DOMAIN WHERE INDEXED>1 AND STATE>=0 AND QUALITY_RAW>=-10")) {
                stmt.setFetchSize(10000);
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    domains.put(rsp.getInt(1), new DomainData("", 0.0, rsp.getInt(1), rsp.getInt(2), rsp.getInt(3)));
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
                    }
                }
            }

            try (var stmt = conn.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE URL_PART=?")) {
                stmt.setFetchSize(10000);

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

    public TIntList pageRank(int resultCount) {
        RankVector rank = new RankVector(1.d / domains.size());

        for (int i = 0; i < 100; i++) {
            RankVector newRank = createNewRankVector(rank);

            double oldNorm = rank.norm();
            double newNorm = newRank.norm();
            double dNorm = oldNorm - newNorm ;
            originDomainIds.forEach(id -> newRank.increment(id, dNorm/oldNorm));
//            newRank.increment(14880, dNorm/rank.norm());
            rank = newRank;
        }

        for (var id : originDomainIds) {
            rank.increment(id, -1);
        }

        return rank.getRanking(resultCount);
    }

    @NotNull
    private RankVector createNewRankVector(RankVector rank) {

        final TIntArrayList empty = new TIntArrayList();

        double rankNorm = rank.norm();
        RankVector newRank = new RankVector(0);

        for (DomainData domain : domains.values(new DomainData[domains.size()])) {

            var links = Optional.ofNullable(linkData.get(domain.id)).orElse(empty);
            if (links.size() > 0) {
                double newRankValue = 0;
                for (int linkedDomain : links.toArray()) {
                    newRankValue += rank.get(linkedDomain) / links.size();
                }

                newRank.set(domain.id, 0.85*newRankValue/rankNorm);
            }
        }
        return newRank;
    }

    private void loadDataFromFile() throws IOException {

        try (var str = Files.lines(Path.of("/home/vlofgren/Work/data-domains.txt"))) {
            str.map(DomainData::new)
                    .filter(domain -> domain.indexed>1)
                    .filter(domain -> domain.state>=1)
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
                }


                if (!reverseLinkData.contains(dst)) {
                    reverseLinkData.put(dst, new TIntArrayList());
                }
                reverseLinkData.get(dst).add(src);
            });
        }
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

        public TIntList getRanking(int numResults) {
            TIntArrayList list = new TIntArrayList(numResults);

            Comparator<DomainData> comparator = Comparator.comparing(e -> rank.get(e.id));

            domains.valueCollection().stream()
                    .sorted(comparator.reversed())
                    .map(DomainData::getId)
                    .limit(numResults)
                    .forEach(list::add);

            return list;
        }

    }
    @Data @AllArgsConstructor
    static class DomainData {

        public DomainData(String str) {
            String[] parts = str.split("\\s+");

            id = Integer.parseInt(parts[0]);
            quality = Double.parseDouble(parts[1]);
            name = parts[2];
            indexed = Integer.parseInt(parts[3]);
            state = Integer.parseInt(parts[4]);
        }
        public final String name;
        public final double quality;
        public final int id;
        public final int indexed;
        public final int state;
    }

}
