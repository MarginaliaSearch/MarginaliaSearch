package nu.marginalia.ranking.domains;

import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.ranking.domains.data.GraphSource;
import org.apache.commons.lang3.StringUtils;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestGraphSourceForInvertedLinkData implements GraphSource {
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
    public Graph<Integer, ?> getGraph() {
        Graph<Integer, ?> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        idToName = new HashMap<>();

        try (var stream = Files
                .lines(domainDataPath))
        {

            stream.skip(1)
                    .mapMultiToInt((line, c) -> {
                        String[] parts = StringUtils.split(line, '\t');
                        int id = Integer.parseInt(parts[0]);
                        String name = parts[1];
                        int node_affinity = Integer.parseInt(parts[3]);
                        if (node_affinity > 0) {
                            c.accept(id);
                            idToName.put(id, parts[1]);
                        }
                    })
                    .forEach(graph::addVertex);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (var path : linksDataPaths) {
            try (var data = LongArrayFactory.mmapForReadingConfined(path)) {
                data.forEach(0, data.size(), (pos, val) -> {

                    val = Long.reverseBytes(val); // data is in "java endian", LongArray is in "C endian"

                    int src = (int) (val >>> 32);
                    int dest = (int) (val & 0xFFFF_FFFFL);

                    if (graph.containsVertex(src) && graph.containsVertex(dest)) {
                        graph.addEdge(dest, src);
                    }
                });
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }


        return graph;
    }

}
