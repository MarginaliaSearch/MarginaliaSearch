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

        assertEquals("q c ( b | d )", graph.compileToQuery());
    }

    @Test
    void testCompile2() {
        // Construct a graph like

        // ^ -  b - c - d - $
        QWordGraph graph = new QWordGraph("b", "c", "d");

        assertEquals("b c d", graph.compileToQuery());
    }

    @Test
    void testCompile3() {
        // Construct a graph like

        // ^ -  a - b - c - $
        //   \- d -/
        QWordGraph graph = new QWordGraph("q", "b", "c");
        graph.addVariant(graph.node("q"), "d");
        assertEquals("b c ( q | d )", graph.compileToQuery());
    }

    @Test
    void testCompile4() {
        // Construct a graph like

        // ^ -  a - b - c - $
        //           \- d -/
        QWordGraph graph = new QWordGraph("q", "b", "c");
        graph.addVariant(graph.node("c"), "d");
        assertEquals("q b ( c | d )", graph.compileToQuery());
    }

    @Test // this test is a bit flaky, the order of the variants is not guaranteed
    void testCompile5() {
        // Construct a graph like

        //       /- e -\
        // ^ -  a - b - c - $
        //           \- d -/
        QWordGraph graph = new QWordGraph("q", "b", "c");
        graph.addVariant(graph.node("c"), "d");
        graph.addVariant(graph.node("b"), "e");
        assertEquals("q ( c ( b | e ) | d ( b | e ) )", graph.compileToQuery());
    }
}