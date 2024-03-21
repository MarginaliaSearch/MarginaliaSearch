package nu.marginalia.functions.searchquery.query_parser.model;

import org.junit.jupiter.api.Test;

class QWordGraphTest {

    @Test
    public void testAddConstructor() {
        QWordGraph graph = new QWordGraph("hello", "world");

        System.out.println(graph.isBypassed(graph.nodes().get(1), QWord.beg(), QWord.end()));
        System.out.println(graph.isBypassed(graph.nodes().get(2), QWord.beg(), QWord.end()));
        System.out.println(graph.compileToQuery());
        graph.links().forEach(System.out::println);
        System.out.println("--");
        graph.nodes().forEach(System.out::println);
        System.out.println("--");
        graph.addVariant(graph.nodes().get(1), "sup");
        System.out.println(graph.compileToQuery());
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
    }
}