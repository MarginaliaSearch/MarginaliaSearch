package nu.marginalia.functions.searchquery.query_parser.model;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Graph structure for constructing query variants.  The graph should be a directed acyclic graph,
 * with a single start node and a single end node, denoted by QWord.beg() and QWord.end() respectively.
 * <p></p>
 * Naively, every path from the start to the end node should represent a valid query variant, although in
 * practice it is desirable to be clever about how to evaluate the paths, to avoid a large number of queries
 * being generated.
 */
public class QWordGraph implements Iterable<QWord> {


    public record QWordGraphLink(QWord from, QWord to) {}

    private final List<QWordGraphLink> links = new ArrayList<>();
    private final Map<Integer, List<QWord>> fromTo = new HashMap<>();
    private final Map<Integer, List<QWord>> toFrom = new HashMap<>();

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


    /** Add a link from the previous word to the next word for every adjacent word in the graph;
     * except for when the provided word is preceeded by the start token and succeeded by the
     * end token. */
    public void addOmitLink(QWord qw) {
        for (var prev : getPrev(qw)) {
            for (var next : getNext(qw)) {
                if (prev.isBeg() && next.isEnd())
                    continue;

                addLink(prev, next);
            }
        }
    }

    public void addLink(QWord from, QWord to) {
        links.add(new QWordGraphLink(from, to));
        fromTo.computeIfAbsent(from.ord(), k -> new ArrayList<>()).add(to);
        toFrom.computeIfAbsent(to.ord(), k -> new ArrayList<>()).add(from);
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

    public QWord node(String word) {
        return nodes().stream()
                .filter(n -> n.word().equals(word))
                .findFirst()
                .orElseThrow();
    }

    public List<QWord> getNext(QWord word) {
        return fromTo.getOrDefault(word.ord(), List.of());
    }
    public List<QWord> getNextOriginal(QWord word) {
        return fromTo.getOrDefault(word.ord(), List.of())
                .stream()
                .filter(QWord::isOriginal)
                .toList();
    }

    public List<QWord> getPrev(QWord word) {
        return toFrom.getOrDefault(word.ord(), List.of());
    }
    public List<QWord> getPrevOriginal(QWord word) {
        return toFrom.getOrDefault(word.ord(), List.of())
                .stream()
                .filter(QWord::isOriginal)
                .toList();
    }

    public Map<QWord, Set<QWord>> forwardReachability() {
        Map<QWord, Set<QWord>> ret = new HashMap<>();

        Set<QWord> edge = Set.of(QWord.beg());
        Set<QWord> visited = new HashSet<>();

        while (!edge.isEmpty()) {
            Set<QWord> next = new LinkedHashSet<>();

            for (var w : edge) {

                for (var n : getNext(w)) {
                    var set = ret.computeIfAbsent(n, k -> new HashSet<>());

                    set.add(w);
                    set.addAll(ret.getOrDefault(w, Set.of()));

                    next.add(n);
                }
            }

            next.removeAll(visited);
            visited.addAll(next);
            edge = next;
        }

        return ret;
    }

    public Map<QWord, Set<QWord>> reverseReachability() {
        Map<QWord, Set<QWord>> ret = new HashMap<>();

        Set<QWord> edge = Set.of(QWord.end());
        Set<QWord> visited = new HashSet<>();

        while (!edge.isEmpty()) {
            Set<QWord> prev = new LinkedHashSet<>();

            for (var w : edge) {

                for (var p : getPrev(w)) {
                    var set = ret.computeIfAbsent(p, k -> new HashSet<>());

                    set.add(w);
                    set.addAll(ret.getOrDefault(w, Set.of()));

                    prev.add(p);
                }
            }

            prev.removeAll(visited);
            visited.addAll(prev);
            edge = prev;
        }

        return ret;
    }

    public record ReachabilityData(List<QWord> sortedNodes,
                            Map<QWord, Integer> sortOrder,

                            Map<QWord, Set<QWord>> forward,
                            Map<QWord, Set<QWord>> reverse)
    {
        public Set<QWord> forward(QWord node) {
            return forward.getOrDefault(node, Set.of());
        }
        public Set<QWord> reverse(QWord node) {
            return reverse.getOrDefault(node, Set.of());
        }

        public Comparator<QWord> topologicalComparator() {
            Comparator<QWord> comp = Comparator.comparing(sortOrder::get);
            return comp.thenComparing(QWord::ord);
        }

    }

    /** Gather data about graph reachability, including the topological order of nodes */
    public ReachabilityData reachability() {
        var forwardReachability = forwardReachability();
        var reverseReachability = reverseReachability();

        List<QWord> nodes = new ArrayList<>(nodes());
        nodes.sort(new SetMembershipComparator<>(forwardReachability));

        Map<QWord, Integer> topologicalOrder = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            topologicalOrder.put(nodes.get(i), i);
        }

        return new ReachabilityData(nodes, topologicalOrder, forwardReachability, reverseReachability);
    }

    static class SetMembershipComparator<T> implements Comparator<T> {
        private final Map<T, Set<T>> membership;

        SetMembershipComparator(Map<T, Set<T>> membership) {
            this.membership = membership;
        }

        @Override
        public int compare(T o1, T o2) {
            return Boolean.compare(isIn(o1, o2), isIn(o2, o1));
        }

        private boolean isIn(T a, T b) {
            return membership.getOrDefault(a, Set.of()).contains(b);
        }
    }

    public String compileToQuery() {
        return QWordPathsRenderer.render(this);
    }
    public String compileToDot() {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph {\n");
        for (var link : links) {
            sb.append(STR."\"\{link.from().word()}\" -> \"\{link.to.word()}\";\n");
        }
        sb.append("}\n");
        return sb.toString();
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
                pos = getNextOriginal(pos).getFirst();
                return pos;
            }
        };
    }
}
