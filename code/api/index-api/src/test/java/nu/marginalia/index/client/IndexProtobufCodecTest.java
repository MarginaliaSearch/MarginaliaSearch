package nu.marginalia.index.client;

import nu.marginalia.index.client.model.query.SearchSubquery;
import nu.marginalia.index.client.model.results.ResultRankingParameters;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.index.query.limit.SpecificationLimit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class IndexProtobufCodecTest {
    @Test
    public void testSpecLimit() {
        verifyIsIdentityTransformation(SpecificationLimit.none(), l -> IndexProtobufCodec.convertSpecLimit(IndexProtobufCodec.convertSpecLimit(l)));
        verifyIsIdentityTransformation(SpecificationLimit.equals(1), l -> IndexProtobufCodec.convertSpecLimit(IndexProtobufCodec.convertSpecLimit(l)));
        verifyIsIdentityTransformation(SpecificationLimit.greaterThan(1), l -> IndexProtobufCodec.convertSpecLimit(IndexProtobufCodec.convertSpecLimit(l)));
        verifyIsIdentityTransformation(SpecificationLimit.lessThan(1), l -> IndexProtobufCodec.convertSpecLimit(IndexProtobufCodec.convertSpecLimit(l)));
    }

    @Test
    public void testRankingParameters() {
        verifyIsIdentityTransformation(ResultRankingParameters.sensibleDefaults(),
                p -> IndexProtobufCodec.convertRankingParameterss(IndexProtobufCodec.convertRankingParameterss(p)));
    }

    @Test
    public void testQueryLimits() {
        verifyIsIdentityTransformation(new QueryLimits(1,2,3,4),
                l -> IndexProtobufCodec.convertQueryLimits(IndexProtobufCodec.convertQueryLimits(l))
                );
    }
    @Test
    public void testSubqery() {
        verifyIsIdentityTransformation(new SearchSubquery(
                List.of("a", "b"),
                List.of("c", "d"),
                List.of("e", "f"),
                List.of("g", "h"),
                List.of(List.of("i", "j"), List.of("k"))
                ),
                s -> IndexProtobufCodec.convertSearchSubquery(IndexProtobufCodec.convertSearchSubquery(s))
        );
    }
    private <T> void verifyIsIdentityTransformation(T val, Function<T,T> transformation) {
        assertEquals(val, transformation.apply(val), val.toString());
    }
}