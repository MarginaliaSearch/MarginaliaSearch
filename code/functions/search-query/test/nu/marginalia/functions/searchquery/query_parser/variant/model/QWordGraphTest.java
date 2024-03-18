package nu.marginalia.functions.searchquery.query_parser.variant.model;

import org.junit.jupiter.api.Test;

class QWordGraphTest {

    @Test
    public void testAddConstructor() {
        QWordGraph graph = new QWordGraph("hello", "world");

        System.out.println(graph.isBypassed(graph.nodes().get(1), QWord.beg(), QWord.end()));
        System.out.println(graph.isBypassed(graph.nodes().get(2), QWord.beg(), QWord.end()));
        graph.links().forEach(System.out::println);
        System.out.println("--");
        graph.nodes().forEach(System.out::println);
        System.out.println("--");
        graph.addVariant(graph.nodes().get(1), "sup");
        System.out.println(graph.isBypassed(graph.nodes().get(1), QWord.beg(), QWord.end()));
        System.out.println(graph.isBypassed(graph.nodes().get(2), QWord.beg(), QWord.end()));
        System.out.println("--");
        graph.links().forEach(System.out::println);
        System.out.println("--");
        graph.nodes().forEach(System.out::println);

        graph.addVariantForSpan(graph.nodes().get(1), graph.nodes().get(2), "heyall");
        System.out.println(graph.isBypassed(graph.nodes().get(1), QWord.beg(), QWord.end()));
        System.out.println(graph.isBypassed(graph.nodes().get(2), QWord.beg(), QWord.end()));
        System.out.println("--");
        graph.links().forEach(System.out::println);
        System.out.println("--");
        graph.nodes().forEach(System.out::println);
    }
}