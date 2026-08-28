package nu.marginalia.api.searchquery;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IndexProtobufCodecTest {

    @Test
    void testRoundTrip() {
        var original = new CompiledQuery<>(
                List.of(IntList.of(0, 1), IntList.of(0, 2), IntList.of(3)),
                new int[] { 0, 1, 1, 3 },
                new String[] { "elden", "ring", "rings", "elden_ring" });

        var decoded = IndexProtobufCodec.convertCompiledQuery(IndexProtobufCodec.convertCompiledQuery(original));

        assertEquals(original.paths, decoded.paths);
        assertArrayEquals(original.variantClasses, decoded.variantClasses);
        assertEquals(original.stream().toList(), decoded.stream().toList());
    }

    @Test
    void testNormalization() {
        var query = IndexProtobufCodec.convertCompiledQuery(RpcCompiledQuery.newBuilder()
                .addAllTerms(List.of("a", "b"))
                .addAllVariantClass(List.of(0, 1))
                .addPaths(RpcTermPath.newBuilder().addAllTerm(List.of(1, 0, 1)))
                .addPaths(RpcTermPath.newBuilder().addAllTerm(List.of(0, 1)))
                .addPaths(RpcTermPath.newBuilder())
                .build());

        assertEquals(List.of(IntList.of(0, 1)), query.paths);
    }

    @Test
    void testInvalidReference() {
        var missingClasses = RpcCompiledQuery.newBuilder()
                .addAllTerms(List.of("a", "b"))
                .addVariantClass(0)
                .addPaths(RpcTermPath.newBuilder().addTerm(0))
                .build();
        assertThrows(IllegalArgumentException.class, () -> IndexProtobufCodec.convertCompiledQuery(missingClasses));

        var badPath = RpcCompiledQuery.newBuilder()
                .addAllTerms(List.of("a", "b"))
                .addAllVariantClass(List.of(0, 1))
                .addPaths(RpcTermPath.newBuilder().addTerm(2))
                .build();
        assertThrows(IllegalArgumentException.class, () -> IndexProtobufCodec.convertCompiledQuery(badPath));

        var badClass = RpcCompiledQuery.newBuilder()
                .addAllTerms(List.of("a", "b"))
                .addAllVariantClass(List.of(0, -1))
                .addPaths(RpcTermPath.newBuilder().addTerm(0))
                .build();
        assertThrows(IllegalArgumentException.class, () -> IndexProtobufCodec.convertCompiledQuery(badClass));
    }

    @Test
    void testJustAnd() {
        var query = CompiledQuery.just("a", "b", "c");

        assertEquals(List.of(IntList.of(0, 1, 2)), query.paths);
        assertArrayEquals(new int[] { 0, 1, 2 }, query.variantClasses);
        assertEquals("[a b c]", query.toString());
    }
}
