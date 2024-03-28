package nu.marginalia.functions.searchquery.query_parser.model;

import java.util.*;
import java.util.stream.Collectors;

/** Renders a set of QWordPaths into a human-readable infix-style expression.  It's not guaranteed to find
 * the globally optimal expression, but rather uses a greedy algorithm as a tradeoff in effort to outcome.
 */
class QWordPathsRenderer {
    private final Set<QWordPath> paths;

    private QWordPathsRenderer(Collection<QWordPath> paths) {
        this.paths = Collections.unmodifiableSet(new HashSet<>(paths));
    }

    private QWordPathsRenderer(QWordGraph graph) {
        this.paths = Collections.unmodifiableSet(QWordGraphPathLister.listPaths(graph));
    }

    public static String render(QWordGraph graph) {
        return new QWordPathsRenderer(graph).render(graph.reachability());
    }

    String render(QWordGraph.ReachabilityData reachability) {
        if (paths.size() == 1) {
            return paths.iterator().next().stream().map(QWord::word).collect(Collectors.joining(" "));
        }

        Map<QWord, Integer> commonality = paths.stream().flatMap(QWordPath::stream)
                .collect(Collectors.groupingBy(w -> w, Collectors.summingInt(w -> 1)));

        Set<QWord> commonToAll = new HashSet<>();
        Set<QWord> notCommonToAll = new HashSet<>();

        commonality.forEach((k, v) -> {
            if (v == paths.size()) {
                commonToAll.add(k);
            } else {
                notCommonToAll.add(k);
            }
        });

        StringJoiner concat = new StringJoiner(" ");
        if (!commonToAll.isEmpty()) { // Case where one or more words are common to all paths

            commonToAll.stream()
                    .sorted(reachability.topologicalComparator())
                    .map(QWord::word)
                    .forEach(concat::add);

            // Deal portion of the paths that do not all share a common word
            if (!notCommonToAll.isEmpty()) {

                List<QWordPath> nonOverlappingPortions = new ArrayList<>();

                for (var path : paths) {
                    // Project the path onto the divergent nodes (i.e. remove common nodes)
                    var np = path.project(notCommonToAll);
                    if (np.isEmpty())
                        continue;
                    nonOverlappingPortions.add(np);
                }

                if (nonOverlappingPortions.size() > 1) {
                    var wp = new QWordPathsRenderer(nonOverlappingPortions);
                    concat.add(wp.render(reachability));
                } else if (!nonOverlappingPortions.isEmpty()) {
                    var wp = new QWordPathsRenderer(nonOverlappingPortions);
                    concat.add(wp.render(reachability));
                }
            }
        } else if (commonality.size() > 1) { // The case where no words are common to all paths

            // Sort the words by commonality, so that we can consider the most common words first
            List<QWord> byCommonality = commonality.entrySet().stream().sorted(Map.Entry.comparingByValue()).map(Map.Entry::getKey).collect(Collectors.toList()).reversed();

            Map<QWord, List<QWordPath>> pathsByCommonWord = new HashMap<>();

            // Mutable copy of the paths
            List<QWordPath> allDivergentPaths = new ArrayList<>(paths);

            for (var commonWord : byCommonality) {
                if (allDivergentPaths.isEmpty())
                    break;

                var iter = allDivergentPaths.iterator();
                while (iter.hasNext()) {
                    var path = iter.next();

                    if (!path.contains(commonWord)) {
                        continue;
                    }

                    pathsByCommonWord
                            .computeIfAbsent(commonWord, k -> new ArrayList<>())
                            .add(path.without(commonWord)); // Remove the common word from the path

                    iter.remove();
                }
            }

            var branches = pathsByCommonWord.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(reachability.topologicalComparator())) // Sort by topological order to ensure consistent output
                    .map(e -> {
                        String commonWord = e.getKey().word();
                        String branchPart = new QWordPathsRenderer(e.getValue()).render(reachability);
                        return STR."\{commonWord} \{branchPart}";
                    })
                    .collect(Collectors.joining(" | ", " ( ", " ) "));

            concat.add(branches);

        }

        // Remove any double spaces that may have been introduced
        return concat.toString().replaceAll("\\s+", " ").trim();
    }

}
