package nu.marginalia.ranking;

import lombok.SneakyThrows;
import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.ranking.data.GraphSource;
import org.apache.commons.lang3.StringUtils;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestGraphSourceForSimilarityData implements GraphSource {
    private static Path domainDataPath = Paths.get("/home/vlofgren/Exports/Links/domains.export.tsv");
    private static Path similarityDataPath = Paths.get("/home/vlofgren/Exports/Links/neighbors.tsv");

    public List<Integer> domainIds(List<String> domainNameList) { return List.of(); }

    static boolean isAvailable() {
        return Files.exists(domainDataPath) && Files.exists(similarityDataPath);
    }

    private Map<Integer, String> idToName = new HashMap<>();

    public String getName(int id) {
        return idToName.get(id);
    }

    @SneakyThrows
    @Override
    public Graph<Integer, ?> getGraph() {
        Graph<Integer, ?> graph = new DefaultUndirectedWeightedGraph<>(DefaultWeightedEdge.class);
        idToName = new HashMap<>();

        try (var stream = Files
                .lines(domainDataPath)) {

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
                    .forEach(graph::addVertex);
        }

        try (var stream = Files
                .lines(similarityDataPath)) {

            stream.skip(1)
                    .forEach(line -> {
                        String[] parts = StringUtils.split(line, '\t');
                        int src = Integer.parseInt(parts[0]);
                        int dest = Integer.parseInt(parts[1]);
                        double weight = Double.parseDouble(parts[2]);
                        if (graph.containsVertex(src) && graph.containsVertex(dest)) {
                            graph.addEdge(src, dest);
                            graph.setEdgeWeight(src, dest, weight);
                        }
                    });
        }

        return graph;
    }

}
