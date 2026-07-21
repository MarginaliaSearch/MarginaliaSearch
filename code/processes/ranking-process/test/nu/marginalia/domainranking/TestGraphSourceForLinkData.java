package nu.marginalia.domainranking;

import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.domaingraph.DomainGraph;
import nu.marginalia.domaingraph.DomainGraphBuilder;
import nu.marginalia.domaingraph.GraphSource;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestGraphSourceForLinkData implements GraphSource {
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

    private boolean loaded = false;

    private Map<Integer, String> idToName = new HashMap<>();

    public String getName(int id) {
        if (!loaded) throw new IllegalStateException("Graph not loaded, run getGraph() first!");

        String name = idToName.get(id);
        if (null == name) {
            return "id:"+Integer.toString(id);
        }
        return name;
    }

    @Override
    public DomainGraph getGraph() {
        DomainGraphBuilder builder = DomainGraphBuilder.directed();
        idToName = new HashMap<>();

        try {
            Files.readAllLines(domainDataPath).forEach(line -> {
                String[] parts = line.split("\t");
                if (!Character.isDigit(parts[0].charAt(0)))
                    return;
                idToName.put(Integer.parseInt(parts[0]), parts[1]);
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (var stream = Files.lines(domainDataPath)) {
            stream.skip(1)
                    .mapMultiToInt((line, c) -> {
                        String[] parts = StringUtils.split(line, '\t');
                        int id = Integer.parseInt(parts[0]);
                        int node_affinity = Integer.parseInt(parts[3]);
                        if (node_affinity > 0) {
                            c.accept(id);
                        }
                    })
                    .forEach(builder::addVertex);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        DomainGraph graph = builder.build(consumer -> {
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

        loaded = true;
        return graph;
    }
}
