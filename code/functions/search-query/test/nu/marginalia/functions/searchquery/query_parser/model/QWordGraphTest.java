package nu.marginalia.functions.searchquery.query_parser.model;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QWordGraphTest {

    @Test
    void forwardReachability() {
        // Construct a graph like

        // ^ -  a - b - c - $
        //       \- d -/
        QWordGraph graph = new QWordGraph("a", "b", "c");
        graph.addVariant(graph.node("b"), "d");

        var reachability = graph.forwardReachability();

        System.out.println(reachability.get(graph.node("a")));
        System.out.println(reachability.get(graph.node("b")));
        System.out.println(reachability.get(graph.node("c")));
        System.out.println(reachability.get(graph.node("d")));

        assertEquals(Set.of(graph.node(" ^ ")), reachability.get(graph.node("a")));
        assertEquals(Set.of(graph.node(" ^ "), graph.node("a")), reachability.get(graph.node("b")));
        assertEquals(Set.of(graph.node(" ^ "), graph.node("a")), reachability.get(graph.node("d")));
        assertEquals(Set.of(graph.node(" ^ "), graph.node("a"), graph.node("b"), graph.node("d")), reachability.get(graph.node("c")));
        assertEquals(Set.of(graph.node(" ^ "), graph.node("a"), graph.node("b"), graph.node("d"), graph.node("c")), reachability.get(graph.node(" $ ")));
    }


    @Test
    void reverseReachability() {
        // Construct a graph like

        // ^ -  a - b - c - $
        //       \- d -/
        QWordGraph graph = new QWordGraph("a", "b", "c");
        graph.addVariant(graph.node("b"), "d");

        var reachability = graph.reverseReachability();

        System.out.println(reachability.get(graph.node("a")));
        System.out.println(reachability.get(graph.node("b")));
        System.out.println(reachability.get(graph.node("c")));
        System.out.println(reachability.get(graph.node("d")));

        assertEquals(Set.of(graph.node(" $ ")), reachability.get(graph.node("c")));
        assertEquals(Set.of(graph.node(" $ "), graph.node("c")), reachability.get(graph.node("b")));
        assertEquals(Set.of(graph.node(" $ "), graph.node("c")), reachability.get(graph.node("d")));
        assertEquals(Set.of(graph.node(" $ "), graph.node("c"), graph.node("b"), graph.node("d")), reachability.get(graph.node("a")));
        assertEquals(Set.of(graph.node(" $ "), graph.node("c"), graph.node("b"), graph.node("d"), graph.node("a")), reachability.get(graph.node(" ^ ")));
    }

    @Test
    void testCompile1() {
        // Construct a graph like

        // ^ -  a - b - c - $
        //       \- d -/
        QWordGraph graph = new QWordGraph("a", "b", "c");
        graph.addVariant(graph.node("b"), "d");

        assertEquals("a c ( b | d )", graph.compileToQuery());
    }

    @Test
    void testCompile2() {
        // Construct a graph like

        // ^ -  a - b - c - $
        QWordGraph graph = new QWordGraph("a", "b", "c");

        assertEquals("a b c", graph.compileToQuery());
    }

    @Test
    void testCompile3() {
        // Construct a graph like

        // ^ -  a - b - c - $
        //   \- d -/
        QWordGraph graph = new QWordGraph("a", "b", "c");
        graph.addVariant(graph.node("a"), "d");
        assertEquals("b c ( a | d )", graph.compileToQuery());
    }

    @Test
    void testCompile4() {
        // Construct a graph like

        // ^ -  a - b - c - $
        //           \- d -/
        QWordGraph graph = new QWordGraph("a", "b", "c");
        graph.addVariant(graph.node("c"), "d");
        assertEquals("a b ( c | d )", graph.compileToQuery());
    }

    @Test
    void testCompile5() {
        // Construct a graph like

        //       /- e -\
        // ^ -  a - b - c - $
        //           \- d -/
        QWordGraph graph = new QWordGraph("a", "b", "c");
        graph.addVariant(graph.node("c"), "d");
        graph.addVariant(graph.node("b"), "e");
        assertEquals("a ( b ( c | d ) | c e )", graph.compileToQuery());
    }
}