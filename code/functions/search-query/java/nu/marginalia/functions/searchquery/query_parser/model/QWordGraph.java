package nu.marginalia.functions.searchquery.query_parser.model;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Graph structure for constructing query variants.  The graph should be a directed acyclic graph,
 * with a single start node and a single end node, denoted by QWord.beg() and QWord.end() respectively.
 * <p></p>
 * Naively, every path from the start to the end node should represent a valid query variant, although in
 * practice it is desirable to be clever about how to evaluate the paths, to avoid combinatorial explosion.
 */
public class QWordGraph implements Iterable<QWord> {


    public record QWordGraphLink(QWord from, QWord to) {}

    private final List<QWordGraphLink> links = new ArrayList<>();
    private final Map<QWord, List<QWord>> fromTo = new HashMap<>();
    private final Map<QWord, List<QWord>> toFrom = new HashMap<>();

    private int wordId = 0;

    public QWordGraph(String... words) {
        this(List.of(words));
    }

    public QWordGraph(List<String> words) {
        QWord beg = QWord.beg();
        QWord end = QWord.end();

        var prev = beg;

        for (String s : words) {
            var word = new QWord(wordId++, s);
            addLink(prev, word);
            prev = word;
        }

        addLink(prev, end);
    }

    public void addVariant(QWord original, String word) {
        var siblings = getVariants(original);
        if (siblings.stream().anyMatch(w -> w.word().equals(word)))
            return;

        var newWord = new QWord(wordId++, original, word);

        for (var prev : getPrev(original))
            addLink(prev, newWord);
        for (var next : getNext(original))
            addLink(newWord, next);
    }

    public void addVariantForSpan(QWord first, QWord last, String word) {
        var newWord = new QWord(wordId++, first, word);

        for (var prev : getPrev(first))
            addLink(prev, newWord);
        for (var next : getNext(last))
            addLink(newWord, next);
    }

    public List<QWord> getVariants(QWord original) {
        var prevNext = getPrev(original).stream()
                .flatMap(prev -> getNext(prev).stream())
                .collect(Collectors.toSet());

        return getNext(original).stream()
                .flatMap(next -> getPrev(next).stream())
                .filter(prevNext::contains)
                .collect(Collectors.toList());
    }


    public void addLink(QWord from, QWord to) {
        links.add(new QWordGraphLink(from, to));
        fromTo.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
        toFrom.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
    }

    public List<QWordGraphLink> links() {
        return Collections.unmodifiableList(links);
    }
    public List<QWord> nodes() {
        return links.stream()
                .flatMap(l -> Stream.of(l.from(), l.to()))
                .sorted(Comparator.comparing(QWord::ord))
                .distinct()
                .collect(Collectors.toList());
    }


    public List<QWord> getNext(QWord word) {
        return fromTo.getOrDefault(word, List.of());
    }
    public List<QWord> getNextOriginal(QWord word) {
        return fromTo.getOrDefault(word, List.of())
                .stream()
                .filter(QWord::isOriginal)
                .toList();
    }

    public List<QWord> getPrev(QWord word) {
        return toFrom.getOrDefault(word, List.of());
    }
    public List<QWord> getPrevOriginal(QWord word) {
        return toFrom.getOrDefault(word, List.of())
                .stream()
                .filter(QWord::isOriginal)
                .toList();
    }

    // Returns true if removing the word would disconnect the graph
    // so that there is no path from 'begin' to 'end'.  This is useful
    // in breaking up the graph into smaller component subgraphs, and
    // understanding which vertexes can be re-ordered without changing
    // the semantics of the encoded query.
    public boolean isBypassed(QWord word, QWord begin, QWord end) {
        Set<QWord> edge = new HashSet<>();
        Set<QWord> visited = new HashSet<>();

        edge.add(begin);

        while (!edge.isEmpty()) {
            Set<QWord> next = new HashSet<>();

            for (var w : edge) {
                // Skip the word we're trying find a bypassing route for
                if (w.ord() == word.ord())
                    continue;

                if (Objects.equals(w, end))
                    return true;

                next.addAll(getNext(w));
            }

            next.removeAll(visited);
            visited.addAll(next);
            edge = next;
        }

        return false;
    }

    /** Returns a set of all nodes that are between 'begin' and 'end' in the graph,
     * including the terminal nodes. This is useful for breaking up the graph into
     * smaller components that can be evaluated in any order.
     * <p></p>
     * It is assumed that there is a path from 'begin' to 'end' in the graph, and no
     * other paths that bypass 'end'.
     * <p></p>
     * The nodes are returned in the order they are encountered in a breadth-first search.
     */
    public List<QWord> nodesBetween(QWord begin, QWord end) {
        List<QWord> edge = new ArrayList<>();
        List<QWord> visited = new ArrayList<>();

        visited.add(begin);
        edge.add(begin);

        while (!edge.isEmpty()) {
            List<QWord> next = new ArrayList<>();

            for (var w : edge) {
                if (Objects.equals(w, end))
                    continue;

                if (w.isEnd()) {
                    assert end.isEnd() : "Graph has a path beyond the specified end vertex " + end;
                }

                next.addAll(getNext(w));
            }

            next.removeAll(visited);
            visited.addAll(next);
            edge = next;
        }

        return visited.stream().distinct().toList();
    }

    /** Returns a list of subgraphs that are connected on the path from
     * 'begin' to 'end'.  This is useful for breaking up the graph into
     * smaller components that can be evaluated in any order.
     * <p></p>
     * The subgraphs are specified by their predecessor and successor nodes,
     *
     */
    public List<QWordGraphLink> getSubgraphs(QWord begin, QWord end) {
        // Short-circuit for the common and simple case
        if (getNext(begin).equals(List.of(end)))
            return List.of(new QWordGraphLink(begin, end));

        List<QWordGraphLink> subgraphs = new ArrayList<>();

        List<QWord> points = nodesBetween(begin, end)
                .stream()
                .filter(w -> !isBypassed(w, begin, end))
                .toList();

        for (int i = 0; i < points.size() - 1; i++) {
            var a = points.get(i);
            var b = points.get(i+1);

            subgraphs.add(new QWordGraphLink(a, b));
        }

        return subgraphs;
    }

    public String compileToQuery() {
        return compileToQuery(QWord.beg(), QWord.end());
    }

    public String compileToQuery(QWord begin, QWord end) {
        StringJoiner sj = new StringJoiner(" ");

        for (var subgraph : getSubgraphs(begin, end)) {
            if (getNext(subgraph.from).equals(List.of(subgraph.to))) {
                if (subgraph.from.isBeg())
                    continue;

                sj.add(subgraph.from.word());
            }
            else {
                StringJoiner branchJoiner = new StringJoiner(" | ", "( ", " )");
                if (Objects.equals(subgraph.from, begin)) {
                    for (QWord path : getNext(subgraph.from)) {
                        branchJoiner.add(compileToQuery(path, subgraph.to));
                    }
                }
                else {
                    branchJoiner.add(compileToQuery(subgraph.from, subgraph.to));
                }
                sj.add(branchJoiner.toString());
            }
        }

        return sj.toString();
    }

    @NotNull
    @Override
    public Iterator<QWord> iterator() {
        return new Iterator<>() {
            QWord pos = QWord.beg();

            @Override
            public boolean hasNext() {
                return !pos.isEnd();
            }

            @Override
            public QWord next() {
                pos = getNextOriginal(pos).get(0);
                return pos;
            }
        };
    }
}
