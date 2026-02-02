package nu.marginalia.domainranking;

import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.index.searchset.RankingSearchSet;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPOutputStream;

public class SmallAdjacency {

    private static Path domainDataPath = Paths.get("/home/vlofgren/Exports/Links/domains.export.tsv");
    private static Path[] linksDataPaths = new Path[] {
            Paths.get("/home/vlofgren/Exports/Links/domain-links-1.dat"),
            Paths.get("/home/vlofgren/Exports/Links/domain-links-2.dat"),
            Paths.get("/home/vlofgren/Exports/Links/domain-links-3.dat"),
            Paths.get("/home/vlofgren/Exports/Links/domain-links-4.dat"),
            Paths.get("/home/vlofgren/Exports/Links/domain-links-5.dat"),
            Paths.get("/home/vlofgren/Exports/Links/domain-links-6.dat"),
            Paths.get("/home/vlofgren/Exports/Links/domain-links-7.dat"),
            Paths.get("/home/vlofgren/Exports/Links/domain-links-8.dat"),
            Paths.get("/home/vlofgren/Exports/Links/domain-links-9.dat")
    };

    Int2ObjectOpenHashMap<String> names = new Int2ObjectOpenHashMap<>();
    Int2ObjectOpenHashMap<IntList> outgoingLinks = new Int2ObjectOpenHashMap<>();
    Int2ObjectOpenHashMap<IntList> inboudLinks = new Int2ObjectOpenHashMap<>();
    Int2IntOpenHashMap linkCount = new Int2IntOpenHashMap();

    @BeforeEach
    public void setUp() {
        try (var stream = Files
                .lines(domainDataPath)) {

            stream.skip(1)
                    .forEach((line) -> {
                        String[] parts = StringUtils.split(line, '\t');
                        int id = Integer.parseInt(parts[0]);
                        String name = parts[1];
                        names.put(id, name);
                    });
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        try (var pw = new PrintWriter(new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of("/tmp/linkgraph.csv.gz")))))) {
            for (var path : linksDataPaths) {
                try (var data = LongArrayFactory.mmapForReadingConfined(path)) {
                    data.forEach(0, data.size(), (pos, val) -> {
                        val = Long.reverseBytes(val); // data is in "java endian", LongArray is in "C endian"

                        int src = (int) (val >>> 32);
                        int dest = (int) (val & 0xFFFF_FFFFL);

                        if (names.containsKey(src) && names.containsKey(dest)) {
                            pw.printf("%s,%s\n", names.get(src).toLowerCase(), names.get(dest).toLowerCase());

                            inboudLinks.computeIfAbsent(src, v -> new IntArrayList()).add(dest);
                            outgoingLinks.computeIfAbsent(dest, v -> new IntArrayList()).add(src);
                        }
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        System.out.println("Loaded!");

    }

    @Test
    public void test() throws IOException {
        System.out.println(TestGraphSourceForLinkData.isAvailable());

        var ss = new RankingSearchSet("small", Path.of("/home/vlofgren/Exports/Links/small.dat"));

        var idList = ss.domainIds();

        IntSet direct = new IntOpenHashSet();
        IntSet reachable = new IntOpenHashSet();
        IntSet reachable2 = new IntOpenHashSet();
        IntSet linking = new IntOpenHashSet();
        IntSet linking2 = new IntOpenHashSet();
        IntSet bidi = new IntOpenHashSet();
        IntSet bidi2 = new IntOpenHashSet();

        direct.addAll(idList);

        for (int id : idList) {
            var outgoing = outgoingLinks.getOrDefault(id, IntList.of());
            for (int outId: outgoing) linkCount.addTo(outId, 1);

            var inbound = inboudLinks.getOrDefault(id, IntList.of());
            for (int inId: inbound) linkCount.addTo(inId, 1);

            reachable.addAll(inbound);
            linking.addAll(outgoing);
        }

        reachable.removeAll(direct);
        linking.removeAll(direct);

        bidi.addAll(reachable);
        bidi.retainAll(linking);

        reachable2.addAll(reachable);
        linking2.addAll(linking);
        bidi2.addAll(bidi);

        reachable2.removeIf(id -> linkCount.get(id) < 5);
        linking2.removeIf(id -> linkCount.get(id) < 5);
        bidi2.removeIf(id -> linkCount.get(id) < 5);

        System.out.println(direct.size());
        System.out.println(reachable.size());
        System.out.println(reachable2.size());
        System.out.println(linking.size());
        System.out.println(linking2.size());
        System.out.println(bidi.size());
        System.out.println(bidi2.size());

        write(direct, "domains_direct.txt");
        write(reachable, "domains_reachable.txt");
        write(reachable2, "domains_reachable5.txt");
        write(linking, "domains_linking.txt");
        write(linking2, "domains_linking5.txt");
        write(bidi, "domains_bidi.txt");
        write(bidi2, "domains_bidi5.txt");
//
//        System.out.println("Reachable:");
//        reachable.intStream().mapToObj(this::describeLink).limit(50).forEach(System.out::println);
//        System.out.println("***");
//
//        System.out.println("Reachable (>1):");
//        reachable.intStream()
//                .filter(id -> linkCount.get(id) > 1)
//                .mapToObj(this::describeLink)
//                .limit(50).forEach(System.out::println);
//        System.out.println("***");
//
//
//        System.out.println("Linking:");
//        linking.intStream().mapToObj(this::describeLink).limit(50).forEach(System.out::println);
//        System.out.println("***");
//        System.out.println("Bidi:");
//        bidi.intStream().mapToObj(this::describeLink).limit(50).forEach(System.out::println);
//        System.out.println("***");

    }

    String describeLink(int id) {
        return String.format("%s (%d)", names.get(id), linkCount.get(id));
    }

    void write(IntSet domains, String fileName) throws IOException {
        Path file = Path.of("/home/vlofgren/Work").resolve(fileName);
        Files.deleteIfExists(file);

        try (var pw = new PrintWriter(new FileWriter(file.toFile()))) {
            for (int id : domains) {
                pw.println(names.get(id));
            }
        }
    }


}
