package nu.marginalia.query.svc;

import nu.marginalia.WmsaHome;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.functions.searchquery.svc.QueryFactory;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.index.query.limit.SpecificationLimitType;
import nu.marginalia.util.language.EnglishDictionary;
import nu.marginalia.util.ngrams.NGramBloomFilter;
import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
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
                new NGramBloomFilter(lm)
        );
    }

    public SearchSpecification parseAndGetSpecs(String query) {
        return queryFactory.createQuery(
                new QueryParams(query, null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        SpecificationLimit.none(),
                        SpecificationLimit.none(),
                        SpecificationLimit.none(),
                        SpecificationLimit.none(),
                        null,
                        new QueryLimits(100, 100, 100, 100),
                        "NONE",
                        QueryStrategy.AUTO,
                        ResultRankingParameters.TemporalBias.NONE)).specs;
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
    public void testQuotedStopwords() {
        {
            // the is a stopword, so it should generate an ngram search term
            var specs = parseAndGetSpecs("\"the shining\"");
            assertEquals(List.of("the_shining"), specs.subqueries.iterator().next().searchTermsInclude);
            assertEquals(List.of(), specs.subqueries.iterator().next().searchTermsAdvice);
            assertEquals(List.of(), specs.subqueries.iterator().next().searchTermCoherences);
        }

        {
            // tde isn't a stopword, so we should get the normal behavior
            var specs = parseAndGetSpecs("\"tde shining\"");
            assertEquals(List.of("tde", "shining"), specs.subqueries.iterator().next().searchTermsInclude);
            assertEquals(List.of("tde_shining"), specs.subqueries.iterator().next().searchTermsAdvice);
            assertEquals(List.of(List.of("tde", "shining")), specs.subqueries.iterator().next().searchTermCoherences);
        }
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