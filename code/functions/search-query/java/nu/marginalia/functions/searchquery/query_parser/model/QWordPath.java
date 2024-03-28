package nu.marginalia.functions.searchquery.query_parser.model;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Represents a path of QWords in a QWordGraph.  Since the order of operations when
 * evaluating a query does not affect its semantics, only performance, the order of the
 * nodes in the path is not significant; thus the path is represented with a set.
 */
public class QWordPath {
    private final Set<QWord> nodes;

    QWordPath(Collection<QWord> nodes) {
        this.nodes = new HashSet<>(nodes);
    }

    public boolean contains(QWord node) {
        return nodes.contains(node);
    }

    /** Construct a new path by removing a word from the path. */
    public QWordPath without(QWord word) {
        Set<QWord> newNodes = new HashSet<>(nodes);
        newNodes.remove(word);
        return new QWordPath(newNodes);
    }

    public Stream<QWord> stream() {
        return nodes.stream();
    }

    public QWordPath project(Set<QWord> nodes) {
        return new QWordPath(this.nodes.stream().filter(nodes::contains).collect(Collectors.toSet()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        QWordPath wordPath = (QWordPath) o;

        return nodes.equals(wordPath.nodes);
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public int size() {
        return nodes.size();
    }

    @Override
    public int hashCode() {
        return nodes.hashCode();
    }

    @Override
    public String toString() {
        return STR."WordPath{nodes=\{nodes}\{'}'}";
    }
}
