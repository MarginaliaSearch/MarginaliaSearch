package nu.marginalia.query.svc;

import nu.marginalia.WmsaHome;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.RpcTemporalBias;
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.functions.searchquery.QueryFactory;
import nu.marginalia.functions.searchquery.query_parser.QueryExpansion;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.index.query.limit.SpecificationLimitType;
import nu.marginalia.segmentation.NgramLexicon;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class QueryFactoryTest {

    static QueryFactory queryFactory;

    @BeforeAll
    public static void setUpAll() throws IOException {

        var lm = WmsaHome.getLanguageModels();

        queryFactory = new QueryFactory(
                new QueryExpansion(new TermFrequencyDict(lm), new NgramLexicon(lm))
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
                        RpcQueryLimits.newBuilder()
                                .setResultsTotal(100)
                                .setResultsByDomain(100)
                                .setTimeoutMs(100)
                                .setFetchSize(100)
                                .build(),
                        "NONE",
                        QueryStrategy.AUTO,
                        RpcTemporalBias.Bias.NONE,
                        NsfwFilterTier.OFF,
                        0), null).specs;
    }


    @Test
    void qsec10() {
        Path webis = Path.of("/home/vlofgren/Exports/qsec10/webis-qsec-10-training-set/webis-qsec-10-training-set-queries.txt");

        if (!Files.exists(webis))
            return;

        try (var lines = Files.lines(webis)) {
            lines.limit(1000).forEach(line -> {
                String[] parts = line.split("\t");
                if (parts.length == 2) {
                    System.out.println(parts[1]);
                    System.out.println(parseAndGetSpecs(parts[1]).getQuery().compiledQuery);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        var subquery = parseAndGetSpecs("physics ?tld:edu").query;
        assertEquals(List.of("tld:edu"), subquery.searchTermsPriority);
        assertEquals("physics", subquery.compiledQuery);
    }

    @Test
    public void testExpansion() {
        var subquery = parseAndGetSpecs("elden ring mechanical keyboard slackware linux duke nukem 3d").query;
        System.out.println(subquery.compiledQuery);
    }

    @Test
    public void testExpansion2() {
        var subquery = parseAndGetSpecs("need for speed").query;
        System.out.println(subquery);

    }

    @Test
    public void testExpansion3() {
        var subquery = parseAndGetSpecs("buy rimonabant buy acomplia");
        System.out.println(subquery);
    }

    @Test
    public void testExpansion4() {
        var subquery = parseAndGetSpecs("The Vietnam of computer science");
        System.out.println(subquery);
    }

    @Test
    public void testExpansion5() {
        var subquery = parseAndGetSpecs("The");
        System.out.println(subquery);
    }

    @Test
    public void testExpansion6() {
        var subquery = parseAndGetSpecs("burning the nerves in the neck");
        System.out.println(subquery);
    }

    @Test
    public void testExpansion7() {
        var subquery = parseAndGetSpecs("amazing work being done");
        System.out.println(subquery);
    }

    @Test
    public void testExpansion8() {
        var subquery = parseAndGetSpecs("success often consists of");
        System.out.println(subquery);
    }


    @Test
    public void testContractionWordNum() {
        var subquery = parseAndGetSpecs("glove 80");

        Assertions.assertTrue(subquery.query.compiledQuery.contains(" glove "));
        Assertions.assertTrue(subquery.query.compiledQuery.contains(" 80 "));
        Assertions.assertTrue(subquery.query.compiledQuery.contains(" glove-80 "));
        Assertions.assertTrue(subquery.query.compiledQuery.contains(" glove80 "));
    }


    @Test
    public void testCplusPlus() {
        var subquery = parseAndGetSpecs("std::vector::push_back vector");
        System.out.println(subquery);
    }

    @Test
    public void testQuotedApostrophe() {
        var subquery = parseAndGetSpecs("\"bob's cars\"");

        System.out.println(subquery);

        Assertions.assertTrue(subquery.query.compiledQuery.contains(" bob "));
        Assertions.assertFalse(subquery.query.compiledQuery.contains(" bob's "));
        Assertions.assertEquals("\"bob's cars\"", subquery.humanQuery);
    }

    @Test
    public void testExpansion9() {
        var subquery = parseAndGetSpecs("pie recipe");

        Assertions.assertTrue(subquery.query.compiledQuery.contains(" category:food "));

        subquery = parseAndGetSpecs("recipe pie");

        Assertions.assertFalse(subquery.query.compiledQuery.contains(" category:food "));
    }

    @Test
    public void testParsing() {
        var subquery = parseAndGetSpecs("strlen()");
        assertEquals("strlen", subquery.query.compiledQuery);
        System.out.println(subquery);
    }

    @Test
    public void testAdvice() {
        var subquery = parseAndGetSpecs("mmap (strlen)");
        assertEquals("mmap", subquery.query.compiledQuery);
        assertEquals(List.of("strlen"), subquery.query.searchTermsAdvice);
        System.out.println(subquery);
    }
}