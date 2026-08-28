package nu.marginalia.functions.searchquery.query_parser.model;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QWordGraphTest {

    @Test
    void testRepetition() {
        QWordGraph graph = new QWordGraph("to", "be", "or", "not", "to", "be");
        var query = graph.compileToQuery();

        assertEquals(List.of("to", "be", "or", "not"), query.stream().toList());
        assertEquals(Set.of(List.of("to", "be", "or", "not")), paths(graph));
    }

    @Test
    void testBridging() {
        QWordGraph graph = new QWordGraph("first", "middle", "end");
        // Bridge "first" directly to "end", making "middle" optional
        graph.addLink(graph.node("first"), graph.node("end"));

        assertEquals(Set.of(List.of("first", "middle", "end"), List.of("first", "end")), paths(graph));
    }

    @Test
    void forwardReachability() {
        // Construct a graph like

        // ^ -  a - b - c - $
        //       \- d -/
        QWordGraph graph = new QWordGraph("q", "b", "c");
        graph.addVariant(graph.node("b"), "d");

        var reachability = graph.forwardReachability();

        System.out.println(reachability.get(graph.node("q")));
        System.out.println(reachability.get(graph.node("b")));
        System.out.println(reachability.get(graph.node("c")));
        System.out.println(reachability.get(graph.node("d")));

        assertEquals(Set.of(graph.node(" ^ ")), reachability.get(graph.node("q")));
        assertEquals(Set.of(graph.node(" ^ "), graph.node("q")), reachability.get(graph.node("b")));
        assertEquals(Set.of(graph.node(" ^ "), graph.node("q")), reachability.get(graph.node("d")));
        assertEquals(Set.of(graph.node(" ^ "), graph.node("q"), graph.node("b"), graph.node("d")), reachability.get(graph.node("c")));
        assertEquals(Set.of(graph.node(" ^ "), graph.node("q"), graph.node("b"), graph.node("d"), graph.node("c")), reachability.get(graph.node(" $ ")));
    }


    @Test
    void reverseReachability() {
        // Construct a graph like

        // ^ -  a - b - c - $
        //       \- d -/
        QWordGraph graph = new QWordGraph("q", "b", "c");
        graph.addVariant(graph.node("b"), "d");

        var reachability = graph.reverseReachability();

        System.out.println(reachability.get(graph.node("q")));
        System.out.println(reachability.get(graph.node("b")));
        System.out.println(reachability.get(graph.node("c")));
        System.out.println(reachability.get(graph.node("d")));

        assertEquals(Set.of(graph.node(" $ ")), reachability.get(graph.node("c")));
        assertEquals(Set.of(graph.node(" $ "), graph.node("c")), reachability.get(graph.node("b")));
        assertEquals(Set.of(graph.node(" $ "), graph.node("c")), reachability.get(graph.node("d")));
        assertEquals(Set.of(graph.node(" $ "), graph.node("c"), graph.node("b"), graph.node("d")), reachability.get(graph.node("q")));
        assertEquals(Set.of(graph.node(" $ "), graph.node("c"), graph.node("b"), graph.node("d"), graph.node("q")), reachability.get(graph.node(" ^ ")));
    }

    @Test
    void testCompile1() {
        // Construct a graph like

        // ^ -  a - b - c - $
        //       \- d -/
        QWordGraph graph = new QWordGraph("q", "b", "c");
        graph.addVariant(graph.node("b"), "d");

        assertEquals(Set.of(List.of("q", "b", "c"), List.of("q", "d", "c")), paths(graph));
    }

    @Test
    void testCompile2() {
        // Construct a graph like

        // ^ -  b - c - d - $
        QWordGraph graph = new QWordGraph("b", "c", "d");

        assertEquals(Set.of(List.of("b", "c", "d")), paths(graph));
    }

    @Test
    void testCompile3() {
        // Construct a graph like

        // ^ -  a - b - c - $
        //   \- d -/
        QWordGraph graph = new QWordGraph("q", "b", "c");
        graph.addVariant(graph.node("q"), "d");
        assertEquals(Set.of(List.of("q", "b", "c"), List.of("d", "b", "c")), paths(graph));
    }

    @Test
    void testCompile4() {
        // Construct a graph like

        // ^ -  a - b - c - $
        //           \- d -/
        QWordGraph graph = new QWordGraph("q", "b", "c");
        graph.addVariant(graph.node("c"), "d");
        assertEquals(Set.of(List.of("q", "b", "c"), List.of("q", "b", "d")), paths(graph));
    }

    @Test
    void testCompile5() {
        // Construct a graph like

        //       /- e -\
        // ^ -  a - b - c - $
        //           \- d -/
        QWordGraph graph = new QWordGraph("q", "b", "c");
        graph.addVariant(graph.node("c"), "d");
        graph.addVariant(graph.node("b"), "e");
        assertEquals(Set.of(
                List.of("q", "b", "c"),
                List.of("q", "b", "d"),
                List.of("q", "e", "c"),
                List.of("q", "e", "d")), paths(graph));
    }

    @Test
    void testVariantClasses() {
        QWordGraph graph = new QWordGraph("elden", "ring");
        graph.addVariant(graph.node("ring"), "rings");
        graph.addVariantForSpan(graph.node("elden"), graph.node("ring"), "elden_ring");

        var query = graph.compileToQuery();

        assertEquals(List.of("elden", "ring", "rings", "elden_ring"), query.stream().toList());

        assertEquals(variantClass(query, "ring"), variantClass(query, "rings"));
        assertEquals(indexOf(query, "ring"), variantClass(query, "rings"));
        assertEquals(indexOf(query, "elden"), variantClass(query, "elden"));
        assertEquals(indexOf(query, "elden_ring"), variantClass(query, "elden_ring"));
    }

    /** The words of each path in query order */
    private static Set<List<String>> paths(QWordGraph graph) {
        var query = graph.compileToQuery();

        Set<List<String>> ret = new HashSet<>();
        for (IntList path : query.paths) {
            List<String> words = new ArrayList<>(path.size());
            for (int idx : path) {
                words.add(query.at(idx));
            }
            ret.add(words);
        }
        return ret;
    }

    private static int indexOf(CompiledQuery<String> query, String term) {
        return query.stream().toList().indexOf(term);
    }

    private static int variantClass(CompiledQuery<String> query, String term) {
        return query.variantClasses[indexOf(query, term)];
    }
}
