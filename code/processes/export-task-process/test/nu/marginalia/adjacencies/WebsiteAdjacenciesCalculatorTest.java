package nu.marginalia.adjacencies;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.domaingraph.DomainGraph;
import nu.marginalia.domaingraph.DomainGraphBuilder;
import nu.marginalia.domaingraph.GraphSource;
import nu.marginalia.process.ProcessConfiguration;
import nu.marginalia.test.TestMigrationLoader;
import nu.marginalia.test.TestUtil;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.mockito.Mockito;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Tag("slow")
public class WebsiteAdjacenciesCalculatorTest {

    @Test
    public void test() throws Exception {
        if (!TestGraphSourceForForwardLinkData.isAvailable()) {
            return;
        }

        var forwardSource = new TestGraphSourceForForwardLinkData();

        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter("/tmp/adjacencies.log")))) {
            new WebsiteAdjacenciesCalculator(forwardSource).run(similarities -> {

                synchronized (this) {
                    pw.println(forwardSource.getName(similarities.domainId()) + ":");
                    similarities.encodedSimilarities().toLongArray();
                    for (long encoded : similarities.encodedSimilarities()) {
                        int otherId = DomainSimilarities.decodeOtherId(encoded);
                        float similarity = DomainSimilarities.deocdeSimilarity(encoded);

                        pw.println("\t" + forwardSource.getName(otherId) + ": " + similarity);
                    }
                    pw.println();
                }

            });
        }
    }
}

class TestGraphSourceForForwardLinkData implements GraphSource {
    // The data is available at
    // https://downloads.marginalia.nu/link-test-data.tar.gz
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

    public List<Integer> domainIds(List<String> domainNameList) { return List.of(); }

    static boolean isAvailable() {
        return Files.exists(domainDataPath) && Files.exists(linksDataPaths[0]);
    }

    private Map<Integer, String> idToName = new HashMap<>();

    public String getName(int id) {
        return idToName.get(id);
    }

    @Override
    public DomainGraph getGraph() {
        DomainGraphBuilder builder = DomainGraphBuilder.directed();
        idToName = new HashMap<>();

        try (var stream = Files.lines(domainDataPath)) {
            stream.skip(1)
                    .mapMultiToInt((line, c) -> {
                        String[] parts = StringUtils.split(line, '\t');
                        int id = Integer.parseInt(parts[0]);
                        String name = parts[1];
                        int node_affinity = Integer.parseInt(parts[3]);
                        if (node_affinity > 0) {
                            c.accept(id);
                            idToName.put(id, name);
                        }
                    })
                    .forEach(builder::addVertex);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        return builder.build(consumer -> {
            for (var path : linksDataPaths) {
                try (var data = LongArrayFactory.mmapForReadingConfined(path)) {
                    data.forEach(0, data.size(), (pos, val) -> {
                        val = Long.reverseBytes(val); // data is in "java endian", LongArray is in "C endian"

                        int src = (int) (val >>> 32);
                        int dest = (int) (val & 0xFFFF_FFFFL);
                        consumer.accept(src, dest);
                    });
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

    }
}