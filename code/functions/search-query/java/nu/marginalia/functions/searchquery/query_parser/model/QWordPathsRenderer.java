package nu.marginalia.functions.searchquery.query_parser.model;

import java.util.*;
import java.util.stream.Collectors;

/** Renders a set of QWordPaths into a human-readable infix-style expression.  It's not guaranteed to find
 * the globally optimal expression, but rather uses a greedy algorithm as a tradeoff in effort to outcome.
 */
public class QWordPathsRenderer {
    private final Set<QWordPath> paths;

    private QWordPathsRenderer(Collection<QWordPath> paths) {
        this.paths = new HashSet<>(paths.size());
        for (var path : paths) {
            if (!path.isEmpty()) {
                this.paths.add(path);
            }
        }
    }

    private QWordPathsRenderer(QWordGraph graph) {
        this(Set.copyOf(QWordGraphPathLister.listPaths(graph)));
    }

    public static String render(QWordGraph graph) {
        return new QWordPathsRenderer(graph).render(graph.reachability());
    }


    private static String render(Collection<QWordPath> paths,
                                 QWordGraph.ReachabilityData reachability)
    {
        return new QWordPathsRenderer(paths).render(reachability);
    }

    /** Render the paths into a human-readable infix-style expression.
     * <p></p>
     * This method is recursive, but the recursion depth is limited by the
     * maximum length of the paths, which is hard limited to a value typically around 10,
     * so we don't need to worry about stack overflows here...
     */
    String render(QWordGraph.ReachabilityData reachability) {
        if (paths.size() == 1) {
            return paths.iterator().next().stream().map(QWord::word).collect(Collectors.joining(" "));
        }

        // Find the commonality of words in the paths

        Map<QWord, Integer> commonality = nodeCommonality(paths);

        // Break the words into two categories: those that are common to all paths, and those that are not

        List<QWord> commonToAll = new ArrayList<>();
        Set<QWord> notCommonToAll = new HashSet<>();
        commonality.forEach((k, v) -> {
            if (v == paths.size()) {
                commonToAll.add(k);
            } else {
                notCommonToAll.add(k);
            }
        });

        StringJoiner resultJoiner = new StringJoiner(" ");

        if (!commonToAll.isEmpty()) { // Case where one or more words are common to all paths
            commonToAll.sort(reachability.topologicalComparator());

            for (var word : commonToAll) {
                resultJoiner.add(word.word());
            }

            // Deal portion of the paths that do not all share a common word
            if (!notCommonToAll.isEmpty()) {

                List<QWordPath> nonOverlappingPortions = new ArrayList<>();

                // Create a new path for each path that does not contain the common words we just printed
                for (var path : paths) {
                    var np = path.project(notCommonToAll);
                    if (np.isEmpty())
                        continue;
                    nonOverlappingPortions.add(np);
                }

                // Recurse into the non-overlapping portions
                resultJoiner.add(render(nonOverlappingPortions, reachability));
            }
        } else if (commonality.size() > 1) { // The case where no words are common to all paths


            // Sort the words by commonality, so that we can consider the most common words first
            Map<QWord, List<QWordPath>> pathsByCommonWord = new HashMap<>();

            // Mutable copy of the paths
            List<QWordPath> allDivergentPaths = new ArrayList<>(paths);

            // Break the paths into branches by the first common word they contain, in order of decreasing commonality
            while (!allDivergentPaths.isEmpty()) {
                QWord mostCommon = mostCommonQWord(allDivergentPaths);

                var iter = allDivergentPaths.iterator();
                while (iter.hasNext()) {
                    var path = iter.next();

                    if (!path.contains(mostCommon)) {
                        continue;
                    }

                    // Remove the common word from the path
                    var newPath = path.without(mostCommon);

                    pathsByCommonWord
                            .computeIfAbsent(mostCommon, k -> new ArrayList<>())
                            .add(newPath);

                    // Remove the path from the list of divergent paths since we've now accounted for it and
                    // we don't want redundant branches:
                    iter.remove();
                }
            }

            var branches = pathsByCommonWord.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(reachability.topologicalComparator())) // Sort by topological order to ensure consistent output
                    .map(e -> {
                        String commonWord = e.getKey().word();

                        // Recurse into the branches:
                        String branchPart = render(e.getValue(), reachability);

                        return STR."\{commonWord} \{branchPart}";
                    })
                    .collect(Collectors.joining(" | ", " ( ", " ) "));

            resultJoiner.add(branches);
        }

        // Remove any double spaces that may have been introduced
        return resultJoiner.toString().replaceAll("\\s+", " ").trim();
    }

    /** Compute how many paths each word is part of */
    private static Map<QWord, Integer> nodeCommonality(Collection<QWordPath> paths) {
        return paths.stream().flatMap(QWordPath::stream)
                .collect(Collectors.groupingBy(w -> w, Collectors.summingInt(w -> 1)));
    }
    private static QWord mostCommonQWord(Collection<QWordPath> paths) {
        assert !paths.isEmpty();

        return nodeCommonality(paths).entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow();
    }
}
