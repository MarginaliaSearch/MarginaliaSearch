package nu.marginalia.functions.searchquery.query_parser.model;

import nu.marginalia.language.WordPatterns;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Set;

/** Utility class for listing each path in a {@link QWordGraph}, from the beginning node to the end.
 * Normally this would be a risk for combinatorial explosion, but in practice the graph will be constructed
 * in a way that avoids this risk.
 * */
public class QWordGraphPathLister {
    private final QWordGraph graph;

    public QWordGraphPathLister(QWordGraph graph) {
        this.graph = graph;
    }

    public static Set<QWordPath> listPaths(QWordGraph graph) {
        return new QWordGraphPathLister(graph).listPaths();
    }

    Set<QWordPath> listPaths() {

        Set<QWordPath> paths = new HashSet<>();
        listPaths(paths, new LinkedList<>(), QWord.beg(), QWord.end());
        return paths;
    }

    void listPaths(Set<QWordPath> acc,
                           LinkedList<QWord> stack,
                           QWord start,
                           QWord end)
    {
        boolean isStopword = WordPatterns.isStopWord(start.word());
        if (!isStopword)
            stack.addLast(start);

        if (Objects.equals(start, end)) {
            var nodes = new HashSet<>(stack);

            // Remove the start and end nodes from the path, as these are
            // not part of the query but merely used to simplify the construction
            // of the graph

            nodes.remove(QWord.beg());
            nodes.remove(QWord.end());

            acc.add(new QWordPath(nodes));
        }
        else {
            for (var next : graph.getNext(start)) {
                listPaths(acc, stack, next, end);
            }
        }

        if (!isStopword)
            stack.removeLast();
    }
}
