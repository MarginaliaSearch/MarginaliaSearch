package nu.marginalia.index.client;

import nu.marginalia.api.searchquery.IndexProtobufCodec;
import nu.marginalia.api.searchquery.model.query.SearchPhraseConstraint;
import nu.marginalia.api.searchquery.model.query.SearchQuery;
import nu.marginalia.api.searchquery.model.query.SpecificationLimit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndexProtobufCodecTest {
    @Test
    public void testSpecLimit() {
        verifyIsIdentityTransformation(SpecificationLimit.none(), l -> IndexProtobufCodec.convertSpecLimit(IndexProtobufCodec.convertSpecLimit(l)));
        verifyIsIdentityTransformation(SpecificationLimit.equals(1), l -> IndexProtobufCodec.convertSpecLimit(IndexProtobufCodec.convertSpecLimit(l)));
        verifyIsIdentityTransformation(SpecificationLimit.greaterThan(1), l -> IndexProtobufCodec.convertSpecLimit(IndexProtobufCodec.convertSpecLimit(l)));
        verifyIsIdentityTransformation(SpecificationLimit.lessThan(1), l -> IndexProtobufCodec.convertSpecLimit(IndexProtobufCodec.convertSpecLimit(l)));
    }

    @Test
    public void testSubqery() {
        verifyIsIdentityTransformation(new SearchQuery(
                "qs",
                List.of("a", "b"),
                List.of("c", "d"),
                List.of("e", "f"),
                List.of("g", "h"),
                List.of(
                        SearchPhraseConstraint.mandatory(List.of("i", "j")),
                        SearchPhraseConstraint.optional(List.of("k")))
                ),
                s -> IndexProtobufCodec.convertRpcQuery(IndexProtobufCodec.convertRpcQuery(s))
        );
    }
    private <T> void verifyIsIdentityTransformation(T val, Function<T,T> transformation) {
        assertEquals(val, transformation.apply(val), val.toString());
    }
}