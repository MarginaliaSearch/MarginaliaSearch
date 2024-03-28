package nu.marginalia.functions.searchquery.query_parser.model;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QWordGraphTest {

    @Test
    public void testAddConstructor() {
        QWordGraph graph = new QWordGraph("hello", "world");

        System.out.println(graph.isBypassed(graph.nodes().get(1), QWord.beg(), QWord.end()));
        System.out.println(graph.isBypassed(graph.nodes().get(2), QWord.beg(), QWord.end()));
        System.out.println(graph.compileToQuery());
        graph.forwardReachability().entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().ord())).forEach(System.out::println);
        graph.links().forEach(System.out::println);
        System.out.println("--");
        graph.nodes().forEach(System.out::println);
        System.out.println("--");
        graph.addVariant(graph.nodes().get(1), "sup");
        System.out.println(graph.compileToQuery());
        graph.forwardReachability().entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().ord())).forEach(System.out::println);
        System.out.println(graph.isBypassed(graph.nodes().get(1), QWord.beg(), QWord.end()));
        System.out.println(graph.isBypassed(graph.nodes().get(2), QWord.beg(), QWord.end()));
        System.out.println("--");
        graph.links().forEach(System.out::println);
        System.out.println("--");
        graph.nodes().forEach(System.out::println);

        graph.addVariantForSpan(graph.nodes().get(1), graph.nodes().get(2), "heyall");
        graph.addVariant(graph.nodes().get(2), "globe");
        System.out.println(graph.compileToQuery());
        System.out.println(graph.isBypassed(graph.nodes().get(1), QWord.beg(), QWord.end()));
        System.out.println(graph.isBypassed(graph.nodes().get(2), QWord.beg(), QWord.end()));
        System.out.println("--");
        graph.links().forEach(System.out::println);
        System.out.println("--");
        graph.nodes().forEach(System.out::println);
        graph.forwardReachability().entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().ord())).forEach(System.out::println);
    }

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

        assertEquals(" ^ a(b|d)c $ ", graph.compileToQuery());
    }
    @Test
    void testCompile2() {
        // Construct a graph like

        // ^ -  a - b - c - $
        QWordGraph graph = new QWordGraph("a", "b", "c");

        assertEquals(" ^ abc $ ", graph.compileToQuery());
    }

    @Test
    void testCompile3() {
        // Construct a graph like

        // ^ -  a - b - c - $
        //   \- d -/
        QWordGraph graph = new QWordGraph("a", "b", "c");
        graph.addVariant(graph.node("a"), "d");
        assertEquals(" ^ (a|d)bc $ ", graph.compileToQuery());
    }

    @Test
    void testCompile4() {
        // Construct a graph like

        // ^ -  a - b - c - $
        //           \- d -/
        QWordGraph graph = new QWordGraph("a", "b", "c");
        graph.addVariant(graph.node("c"), "d");
        assertEquals(" ^ ab(c|d) $ ", graph.compileToQuery());
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
        assertEquals(" ^ a(b|e)(c|d) $ ", graph.compileToQuery());
    }
}