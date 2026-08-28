package nu.marginalia.functions.searchquery.query_parser.model;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;

import java.util.*;
import java.util.stream.Collectors;

/** Translates a {@link QWordGraph} into the term table and paths that the index evaluates.
 */
public class QWordGraphCompiler {

    public static CompiledQuery<String> compile(QWordGraph graph) {
        final Set<QWordPath> paths = QWordGraphPathLister.listPaths(graph);
        final List<QWord> words = new ArrayList<>();
        final Map<String, Integer> termIndices = new LinkedHashMap<>();

        // Outputs:
        final List<IntList> termPaths = new ArrayList<>(paths.size());
        final int[] classes;
        final String[] terms;

        // Build a map from word to a unique index that roughly correlates to a position in the query
        paths.stream().flatMap(p -> p.stream())
                .distinct()
                .sorted(graph.reachability().topologicalComparator())
                .forEach(word -> {
                    if (termIndices.putIfAbsent(word.word(), termIndices.size()) == null) {
                        words.add(word);
                    }
                });

        terms = termIndices.keySet().toArray(String[]::new);

        // Translate each path in the query to a list of indices

        for (var path : paths) {
            int[] indices = path.stream()
                    .mapToInt(word -> termIndices.get(word.word()))
                    .distinct()
                    .sorted()
                    .toArray();
            termPaths.add(IntList.of(indices));
        }

        // Sort for determinism
        termPaths.sort(IntList::compareTo);

        // Calculate variant classes
        Map<QWord, Slot> slots = new HashMap<>(words.size());
        Map<Slot, QWord> representatives = new HashMap<>();

        for (var word : words) {
            slots.put(word, new Slot(Set.copyOf(graph.getPrev(word)), Set.copyOf(graph.getNext(word))));
        }

        // Find representative word for each slot
        slots.forEach((word, slot) -> {
            representatives.merge(slot, word, (a, b) -> a.ord() <= b.ord() ? a : b);
        });

        classes = new int[termIndices.size()];

        for (QWord word : words) {
            QWord representative = representatives.get(slots.get(word));
            classes[termIndices.get(word.word())] = termIndices.get(representative.word());
        }

        return new CompiledQuery<>(termPaths, classes, terms);
    }

    private record Slot(Set<QWord> prev, Set<QWord> next) {}
}
