package nu.marginalia.browse.experimental;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.SneakyThrows;
import org.roaringbitmap.RoaringBitmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static nu.marginalia.browse.experimental.AndCardIntSet.andCardinality;
import static nu.marginalia.browse.experimental.AndCardIntSet.weightedProduct;

public class EdgeWordWordConsineSimilarityMain {
    final Object2IntOpenHashMap<String> stringIds;
    final AndCardIntSet[] dToSMap;
    final float[] weights;
    final boolean useWeights = false;

    enum Direction {
        S_TO_D,
        D_TO_S
    }

    final Direction direction = Direction.D_TO_S;

    public EdgeWordWordConsineSimilarityMain(Path dataFile) throws IOException {
        System.out.println("String IDs");
        stringIds = mapStringsToIds(dataFile);

        System.out.println("DtoS Map");
        dToSMap = constructDtoSMap(dataFile, stringIds);

        System.out.println("Weights");

        if (useWeights) {
            weights = new float[stringIds.size()];
            for (int i = 0; i < stringIds.size(); i++) {
                weights[i] = getWeight(i);
            }
        }
        else {
            weights = null;
        }

        System.out.println("Ready");
    }

    private Object2IntOpenHashMap<String> mapStringsToIds(Path dataFile) throws IOException {
        Object2IntOpenHashMap<String> stringIds = new Object2IntOpenHashMap<>(15_000_000);

        try (var lines = Files.lines(dataFile, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                int tab = line.indexOf('\t');
                if (tab <= 0)
                    return;

                // direction doesn't matter here
                String from = line.substring(0, tab);
                String to = line.substring(tab + 1);

                stringIds.putIfAbsent(from, stringIds.size());
                stringIds.putIfAbsent(to, stringIds.size());
            });
        }
        return stringIds;
    }

    private AndCardIntSet[] constructDtoSMap(Path dataFile, Object2IntOpenHashMap<String> stringIds) throws IOException {
        Map<Integer, RoaringBitmap> tmpMap = new HashMap<>(15_000_000);

        try (var lines = Files.lines(dataFile, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                int tab = line.indexOf('\t');
                if (tab <= 0) return;

                String from, to;
                if (direction == Direction.S_TO_D) {
                    from = line.substring(0, tab);
                    to = line.substring(tab + 1);
                }
                else {
                    from = line.substring(tab + 1);
                    to = line.substring(0, tab);
                }

                tmpMap.computeIfAbsent(stringIds.getInt(to), this::createBitmapWithSelf).add(stringIds.getInt(from));
            });
        }

        AndCardIntSet[] dToSMap = new AndCardIntSet[stringIds.size()];
        tmpMap.entrySet().stream()
                .filter(e -> isEligible(e.getValue()))
                .forEach(e -> dToSMap[e.getKey()] = AndCardIntSet.of(e.getValue()));

        return dToSMap;
    }

    private boolean isEligible(RoaringBitmap value) {
        int cardinality = value.getCardinality();

        return cardinality > 50;
    }

    @SneakyThrows
    public void tryDomains(String... word) {

        System.out.println(Arrays.toString(word));

        int[] domainIds = Arrays.stream(word).mapToInt(stringIds::getInt).toArray();

        long start = System.currentTimeMillis();
        findAdjacentDtoS(new IntOpenHashSet(domainIds), similarities -> {
            Set<Integer> ids = similarities.similarities().stream().map(Similarity::id).collect(Collectors.toSet());

            Map<Integer, String> reveseIds = new HashMap<>(similarities.similarities.size());

            stringIds.forEach((str, id) -> {
                if (ids.contains(id)) {
                    reveseIds.put(id, str);
                }
            });

            for (var similarity : similarities.similarities()) {
                System.out.println(reveseIds.get(similarity.id) + "\t" + dToSMap[similarity.id].getCardinality() + "\t" + prettyPercent(similarity.value));
            }
        });

        System.out.println(System.currentTimeMillis() - start);
    }

    private String prettyPercent(double val) {
        return String.format("%2.2f%%", 100. * val);
    }


    public RoaringBitmap createBitmapWithSelf(int val) {
        var bm = new RoaringBitmap();
        bm.add(val);
        return bm;
    }

    double cosineSimilarity(AndCardIntSet a, AndCardIntSet b) {
        double andCardinality = andCardinality(a, b);
        andCardinality /= Math.sqrt(a.getCardinality());
        andCardinality /= Math.sqrt(b.getCardinality());
        return andCardinality;
    }

    double expensiveCosineSimilarity(AndCardIntSet a, AndCardIntSet b) {
        return weightedProduct(weights, a, b) / Math.sqrt(a.mulAndSum(weights) * b.mulAndSum(weights));
    }

    float getWeight(int i) {
        var vector = dToSMap[i];

        if (vector == null) return 1.0f;
        return 1.0f / (float) Math.log(2+vector.getCardinality());
    }

    record Similarities(int id, List<Similarity> similarities) {};
    record Similarity(int id, double value) {};

    @SneakyThrows
    private void findAdjacentDtoS(IntSet ids, Consumer<Similarities> andThen) {


        AndCardIntSet[] vectors = ids.intStream().mapToObj(id -> dToSMap[id]).toArray(AndCardIntSet[]::new);
        for (var vector : vectors) {
            if (null == vector)
                return;
        }

        var vector = Arrays.stream(vectors).reduce(AndCardIntSet::and).orElseThrow();

        List<Similarity> similarities = IntStream.range(0, dToSMap.length).parallel().mapToObj(
                id -> vectorSimilarity(ids, vector, id))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Similarity::value))
                .toList();


        andThen.accept(new Similarities(0, similarities));
    }

    double cardinalityLimit = 0.1;

    private Similarity vectorSimilarity(IntSet ids, AndCardIntSet vector, int id) {

        /* The minimum cardinality a vector can have so that
         *
         * a (x) b
         * ------- < k is given by k^2
         * |a||b|
         *
         */

        final double cardMin = Math.min(2, cardinalityLimit * cardinalityLimit * vector.getCardinality());

        if (ids.contains(id) || id >= dToSMap.length)
            return null;

        var otherVec = dToSMap[id];
        if (otherVec == null || otherVec.getCardinality() < cardMin)
            return null;

        double similarity = cosineSimilarity(vector, otherVec);

        if (similarity > 0.1) {
            if (useWeights) {
                var recalculated = expensiveCosineSimilarity(vector, otherVec);
                if (recalculated > 0.1) {
                    return new Similarity(id, recalculated);
                }
            }
            else {
                return new Similarity(id, similarity);
            }
        }

        return null;
    }

    public static void main(String[] args) throws IOException {

        var main = new EdgeWordWordConsineSimilarityMain(Path.of(args[0]));

        for (;;) {
            String line = System.console().readLine("Words> ");
            if (line == null || line.isBlank()) {
                break;
            }

            main.tryDomains(line.split("\\s+"));
        }
    }

}
