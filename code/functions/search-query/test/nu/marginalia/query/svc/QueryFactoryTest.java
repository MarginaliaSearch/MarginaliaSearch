package nu.marginalia.query.svc;

import nu.marginalia.WmsaHome;
import nu.marginalia.api.searchquery.*;
import nu.marginalia.api.searchquery.model.CompiledSearchFilterSpec;
import nu.marginalia.api.searchquery.model.query.*;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.functions.searchquery.QueryFactory;
import nu.marginalia.functions.searchquery.query_parser.QueryExpansion;
import nu.marginalia.language.config.LanguageConfigLocation;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.segmentation.NgramLexicon;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class QueryFactoryTest {

    static QueryFactory queryFactory;

    @BeforeAll
    public static void setUpAll() throws IOException, ParserConfigurationException, SAXException {

        var lm = WmsaHome.getLanguageModels();

        DbDomainQueries domainQueriesMock = Mockito.mock(DbDomainQueries.class);
        when(domainQueriesMock.tryGetDomainId(any())).thenReturn(OptionalInt.of(451));

        queryFactory = new QueryFactory(new QueryExpansion(new TermFrequencyDict(lm), new NgramLexicon(lm)),
                domainQueriesMock,
                new LanguageConfiguration(lm, new LanguageConfigLocation.Experimental()));
    }

    public RpcIndexQuery parseAndGetQuery(String query) {
        return queryFactory.createQuery(
                RpcQsQuery.newBuilder()
                        .setHumanQuery(query)
                        .setLangIsoCode("en")
                        .build(),
                CompiledSearchFilterSpec.builder("test", "test").build(),
                null).indexQuery;
    }

    public ProcessedQuery parse(String query) {
        return queryFactory.createQuery(
                RpcQsQuery.newBuilder()
                        .setHumanQuery(query)
                        .setLangIsoCode("en")
                        .build(),
                CompiledSearchFilterSpec.builder("test", "test").build(),
                null);
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
                    System.out.println(parseAndGetQuery(parts[1]).getQuery().getCompiledQuery());
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testParseNoSpecials() {
        var year = parseAndGetQuery("in the year 2000").getYear();
        var size = parseAndGetQuery("in the year 2000").getSize();
        var quality = parseAndGetQuery("in the year 2000").getQuality();

        assertEquals(RpcSpecLimit.TYPE.NONE, year.getType());
        assertEquals(RpcSpecLimit.TYPE.NONE, size.getType());
        assertEquals(RpcSpecLimit.TYPE.NONE, quality.getType());
    }

    @Test
    public void testParseSite() {
        var query = parse("plato site:en.wikipedia.org");
        Assertions.assertEquals("en.wikipedia.org", query.domain);
        Assertions.assertEquals(List.of(), query.indexQuery.getQuery().getAdviceList());
        Assertions.assertEquals(List.of("plato"), query.indexQuery.getQuery().getIncludeList());
        Assertions.assertEquals(List.of(451), query.indexQuery.getRequiredDomainIdsList());
    }

    @Test
    public void testParseSite__only_site_tag() {
        // This is a special flow that ensures we are enable to enumerate all documents for a domain

        var query = parse("site:en.wikipedia.org");
        Assertions.assertEquals("en.wikipedia.org", query.domain);
        Assertions.assertEquals(List.of(), query.indexQuery.getQuery().getAdviceList());
        Assertions.assertEquals(List.of("site:en.wikipedia.org"), query.indexQuery.getQuery().getIncludeList());
        Assertions.assertEquals(List.of(451), query.indexQuery.getRequiredDomainIdsList());
    }

    @Test
    public void testParseSiteWildcard() {
        var query = parse("plato site:*.wikipedia.org");
        Assertions.assertEquals("wikipedia.org", query.domain);
        Assertions.assertEquals(List.of("site:wikipedia.org"), query.indexQuery.getQuery().getAdviceList());
        Assertions.assertEquals(List.of("plato"), query.indexQuery.getQuery().getIncludeList());
        Assertions.assertTrue(query.indexQuery.getRequiredDomainIdsList().isEmpty());
    }

    @Test
    public void testParseSiteWildcard__only_site_tag() {
        // This is a special flow that ensures we are enable to enumerate all documents for a domain

        var query = parse("site:*.wikipedia.org");
        Assertions.assertEquals("wikipedia.org", query.domain);
        Assertions.assertEquals(List.of(), query.indexQuery.getQuery().getAdviceList());
        Assertions.assertEquals(List.of("site:wikipedia.org"), query.indexQuery.getQuery().getIncludeList());
        Assertions.assertTrue(query.indexQuery.getRequiredDomainIdsList().isEmpty());
    }

    @Test
    public void testParseYearEq() {
        var year = parseAndGetQuery("year=2000").getYear();
        assertEquals(RpcSpecLimit.TYPE.EQUALS, year.getType());
        assertEquals(2000, year.getValue());
    }

    @Test
    public void testParseYearLt() {
        var year = parseAndGetQuery("year<2000").getYear();
        assertEquals(RpcSpecLimit.TYPE.LESS_THAN, year.getType());
        assertEquals(2000, year.getValue());
    }

    @Test
    public void testParseYearGt() {
        var year = parseAndGetQuery("year>2000").getYear();
        assertEquals(RpcSpecLimit.TYPE.GREATER_THAN, year.getType());
        assertEquals(2000, year.getValue());
    }

    @Test
    public void testParseSizeEq() {
        var size = parseAndGetQuery("size=2000").getSize();
        assertEquals(RpcSpecLimit.TYPE.EQUALS, size.getType());
        assertEquals(2000, size.getValue());
    }

    @Test
    public void testParseSizeLt() {
        var size = parseAndGetQuery("size<2000").getSize();
        assertEquals(RpcSpecLimit.TYPE.LESS_THAN, size.getType());
        assertEquals(2000, size.getValue());
    }

    @Test
    public void testParseSizeGt() {
        var size = parseAndGetQuery("size>2000").getSize();
        assertEquals(RpcSpecLimit.TYPE.GREATER_THAN, size.getType());
        assertEquals(2000, size.getValue());
    }

    @Test
    public void testParseQualityEq() {
        var quality = parseAndGetQuery("q=2000").getQuality();
        assertEquals(RpcSpecLimit.TYPE.EQUALS, quality.getType());
        assertEquals(2000, quality.getValue());
    }

    @Test
    public void testParseQualityLt() {
        var quality = parseAndGetQuery("q<2000").getQuality();
        assertEquals(RpcSpecLimit.TYPE.LESS_THAN, quality.getType());
        assertEquals(2000, quality.getValue());
    }

    @Test
    public void testParseQualityGt() {
        var quality = parseAndGetQuery("q>2000").getQuality();
        assertEquals(RpcSpecLimit.TYPE.GREATER_THAN, quality.getType());
        assertEquals(2000, quality.getValue());
    }

    @Test
    public void testPriorityTerm() {
        var subquery = parseAndGetQuery("physics ?tld:edu").getQuery();
        assertEquals(List.of("tld:edu"), subquery.getPriorityList());
        assertEquals("physics", subquery.getCompiledQuery());
    }

    @Test
    public void testExpansion() {
        var subquery = parseAndGetQuery("elden ring mechanical keyboard slackware linux duke nukem 3d").getQuery();
        System.out.println(subquery.getCompiledQuery());
    }

    @Test
    public void testExpansion2() {
        var subquery = parseAndGetQuery("need for speed").getQuery();
        System.out.println(subquery);

    }

    @Test
    public void testExpansion3() {
        var subquery = parseAndGetQuery("buy rimonabant buy acomplia");
        System.out.println(subquery);
    }

    @Test
    public void testExpansion4() {
        var subquery = parseAndGetQuery("The Vietnam of computer science");
        System.out.println(subquery);
    }

    @Test
    public void testExpansion5() {
        var subquery = parseAndGetQuery("The");
        System.out.println(subquery);
    }

    @Test
    public void testExpansion6() {
        var subquery = parseAndGetQuery("burning the nerves in the neck");
        System.out.println(subquery);
    }

    @Test
    public void testExpansion7() {
        var subquery = parseAndGetQuery("amazing work being done");
        System.out.println(subquery);
    }

    @Test
    public void testExpansion8() {
        var subquery = parseAndGetQuery("success often consists of");
        System.out.println(subquery);
    }


    @Test
    public void testExpansion10() {
        var subquery = parseAndGetQuery("when was captain james cook born");
        System.out.println(subquery);
    }

    @Test
    public void testContractionWordNum() {
        var subquery = parseAndGetQuery("glove 80");

        Assertions.assertTrue(subquery.getQuery().getCompiledQuery().contains(" glove "));
        Assertions.assertTrue(subquery.getQuery().getCompiledQuery().contains(" 80 "));
        Assertions.assertTrue(subquery.getQuery().getCompiledQuery().contains(" glove-80 "));
        Assertions.assertTrue(subquery.getQuery().getCompiledQuery().contains(" glove80 "));
    }


    @Test
    public void testCplusPlus() {
        var subquery = parseAndGetQuery("std::vector::push_back vector");
        System.out.println(subquery);
    }

    @Test
    public void testQuotedApostrophe() {
        var subquery = parseAndGetQuery("\"bob's cars\"");

        System.out.println(subquery);

        Assertions.assertTrue(subquery.getQuery().getCompiledQuery().contains(" bob "));
        Assertions.assertFalse(subquery.getQuery().getCompiledQuery().contains(" bob's "));
    }

    @Test
    public void testExpansion9() {
        var subquery = parseAndGetQuery("pie recipe");

        Assertions.assertTrue(subquery.getQuery().getCompiledQuery().contains(" category:food "));

        subquery = parseAndGetQuery("recipe pie");

        Assertions.assertFalse(subquery.getQuery().getCompiledQuery().contains(" category:food "));
    }

    @Test
    public void testParsing() {
        var subquery = parseAndGetQuery("strlen()");
        assertEquals("strlen", subquery.getQuery().getCompiledQuery());
        System.out.println(subquery);
    }

    @Test
    public void testAdvice() {
        var subquery = parseAndGetQuery("mmap (strlen)");
        assertEquals("mmap", subquery.getQuery().getCompiledQuery());
        assertEquals(List.of("strlen"), subquery.getQuery().getAdviceList());
        System.out.println(subquery);
    }
}