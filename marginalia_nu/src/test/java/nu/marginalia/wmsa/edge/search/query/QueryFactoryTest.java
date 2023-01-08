package nu.marginalia.wmsa.edge.search.query;

import nu.marginalia.wmsa.configuration.WmsaHome;
import nu.marginalia.wmsa.edge.assistant.dict.NGramBloomFilter;
import nu.marginalia.wmsa.edge.assistant.dict.TermFrequencyDict;
import nu.marginalia.wmsa.edge.model.search.EdgeSearchSpecification;
import nu.marginalia.wmsa.edge.model.search.domain.SpecificationLimitType;
import nu.marginalia.wmsa.edge.search.command.SearchJsParameter;
import nu.marginalia.wmsa.edge.search.model.EdgeSearchProfile;
import nu.marginalia.wmsa.edge.search.query.model.EdgeUserSearchParameters;
import nu.marginalia.wmsa.edge.search.valuation.SearchResultValuator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QueryFactoryTest {

    static QueryFactory queryFactory;

    @BeforeAll
    public static void setUpAll() throws IOException {

        var lm = WmsaHome.getLanguageModels();
        var tfd = new TermFrequencyDict(lm);

        queryFactory = new QueryFactory(lm,
                tfd,
                new EnglishDictionary(tfd),
                new NGramBloomFilter(lm),
                new SearchResultValuator(tfd),
                null
        );
    }

    public EdgeSearchSpecification parseAndGetSpecs(String query) {
        return queryFactory.createQuery(
                new EdgeUserSearchParameters(query, EdgeSearchProfile.CORPO, SearchJsParameter.DEFAULT)
        ).specs;
    }

    @Test
    public void testParseNoSpecials() {
        var year = parseAndGetSpecs("in the year 2000").year;
        var size = parseAndGetSpecs("in the year 2000").size;
        var quality = parseAndGetSpecs("in the year 2000").quality;

        assertEquals(SpecificationLimitType.NONE, year.type());
        assertEquals(SpecificationLimitType.NONE, size.type());
        assertEquals(SpecificationLimitType.NONE, quality.type());
    }


    @Test
    public void testParseYearEq() {
        var year = parseAndGetSpecs("year=2000").year;
        assertEquals(SpecificationLimitType.EQUALS, year.type());
        assertEquals(2000, year.value());
    }

    @Test
    public void testParseYearLt() {
        var year = parseAndGetSpecs("year<2000").year;
        assertEquals(SpecificationLimitType.LESS_THAN, year.type());
        assertEquals(2000, year.value());
    }

    @Test
    public void testParseYearGt() {
        var year = parseAndGetSpecs("year>2000").year;
        assertEquals(SpecificationLimitType.GREATER_THAN, year.type());
        assertEquals(2000, year.value());
    }

    @Test
    public void testParseSizeEq() {
        var size = parseAndGetSpecs("size=2000").size;
        assertEquals(SpecificationLimitType.EQUALS, size.type());
        assertEquals(2000, size.value());
    }

    @Test
    public void testParseSizeLt() {
        var size = parseAndGetSpecs("size<2000").size;
        assertEquals(SpecificationLimitType.LESS_THAN, size.type());
        assertEquals(2000, size.value());
    }

    @Test
    public void testParseSizeGt() {
        var size = parseAndGetSpecs("size>2000").size;
        assertEquals(SpecificationLimitType.GREATER_THAN, size.type());
        assertEquals(2000, size.value());
    }


    @Test
    public void testParseQualityEq() {
        var quality = parseAndGetSpecs("q=2000").quality;
        assertEquals(SpecificationLimitType.EQUALS, quality.type());
        assertEquals(2000, quality.value());
    }

    @Test
    public void testParseQualityLt() {
        var quality = parseAndGetSpecs("q<2000").quality;
        assertEquals(SpecificationLimitType.LESS_THAN, quality.type());
        assertEquals(2000, quality.value());
    }

    @Test
    public void testParseQualityGt() {
        var quality = parseAndGetSpecs("q>2000").quality;
        assertEquals(SpecificationLimitType.GREATER_THAN, quality.type());
        assertEquals(2000, quality.value());
    }

    @Test
    public void testPriorityTerm() {
        var subquery = parseAndGetSpecs("physics ?tld:edu").subqueries.iterator().next();
        assertEquals(List.of("tld:edu"), subquery.searchTermsPriority);
        assertEquals(List.of("physics"), subquery.searchTermsInclude);
    }
}