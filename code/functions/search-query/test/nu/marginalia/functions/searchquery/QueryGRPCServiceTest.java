package nu.marginalia.functions.searchquery;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.WmsaHome;
import nu.marginalia.api.searchquery.RpcQsFilterIdentifier;
import nu.marginalia.api.searchquery.RpcQsQuery;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.RpcSpecLimit;
import nu.marginalia.api.searchquery.model.SearchFilterDefaults;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.functions.searchquery.query_parser.QueryExpansion;
import nu.marginalia.functions.searchquery.searchfilter.SearchFilterCache;
import nu.marginalia.functions.searchquery.searchfilter.SearchFilterParser;
import nu.marginalia.functions.searchquery.searchfilter.SearchFilterStore;
import nu.marginalia.language.config.LanguageConfigLocation;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.nsfw.NsfwDomainFilter;
import nu.marginalia.segmentation.NgramLexicon;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.List;

@Tag("slow")
@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
class QueryGRPCServiceTest {
    static QueryGRPCService service;
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    static HikariDataSource dataSource;

    @BeforeAll
    public static void setUp() throws IOException, ParserConfigurationException, SAXException {

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);
        TestMigrationLoader.flywayMigration(dataSource);

        LanguageConfiguration languageConfiguration = new LanguageConfiguration(WmsaHome.getLanguageModels(), new LanguageConfigLocation.Experimental());
        TermFrequencyDict termFrequencyDict = new TermFrequencyDict(WmsaHome.getLanguageModels());
        NgramLexicon ngramLexicon = new NgramLexicon(WmsaHome.getLanguageModels());

        DbDomainQueries domainQueries = new DbDomainQueries(dataSource);

        var filterStore = new SearchFilterStore(dataSource, new SearchFilterParser());
        filterStore.loadDefaultConfigs();

        service = new QueryGRPCService(
                new QueryFactory(
                        new QueryExpansion(termFrequencyDict, ngramLexicon),
                        domainQueries,
                        languageConfiguration
                ),
                new NsfwDomainFilter(dataSource, List.of(), List.of()),
                null,
                filterStore,
                new SearchFilterCache(filterStore, domainQueries)
        );

    }

    @AfterAll
    public static void shutDown() {
        dataSource.close();
        mariaDBContainer.close();
    }

    final RpcQueryLimits defaultLimits = RpcQueryLimits.newBuilder()
            .setResultsTotal(5)
            .setResultsByDomain(1)
            .setTimeoutMs(1000)
            .build();

    @Test
    void createQuery__by_identifier__docs() {
        var maybeQuery = service.createQuery(
                RpcQsQuery.newBuilder()
                        .setHumanQuery("test")
                        .setQueryLimits(defaultLimits)
                        .setLangIsoCode("en")
                        .setFilterIdentifier(RpcQsFilterIdentifier.newBuilder()
                                .setUserId("SYSTEM")
                                .setIdentifier(SearchFilterDefaults.DOCS.name())
                                .build())
                        .build()
        );
        Assertions.assertTrue(maybeQuery.isPresent());

        var query = maybeQuery.get().indexQuery;

        System.out.println(query);

        Assertions.assertEquals(List.of("test"), query.getTerms().getTermsQueryList());
        Assertions.assertEquals(List.of("generator:docs"), query.getTerms().getTermsRequireList());
        Assertions.assertEquals(defaultLimits, query.getQueryLimits());
        Assertions.assertEquals("en", query.getLangIsoCode());
        Assertions.assertEquals("test", query.getHumanQuery());
    }


    @Test
    void createQuery__by_identifier__docs__with_query_modifiers() {
        var maybeQuery = service.createQuery(
                RpcQsQuery.newBuilder()
                        .setHumanQuery("test (test2) ?test3 -test4 year<2015 q>5 rank=10 size<11 qs=RF_SITE")
                        .setQueryLimits(defaultLimits)
                        .setLangIsoCode("en")
                        .setFilterIdentifier(RpcQsFilterIdentifier.newBuilder()
                                .setUserId("SYSTEM")
                                .setIdentifier(SearchFilterDefaults.DOCS.name())
                                .build())
                        .build()
        );
        Assertions.assertTrue(maybeQuery.isPresent());

        var query = maybeQuery.get().indexQuery;

        System.out.println(query);

        Assertions.assertEquals(List.of("test"), query.getTerms().getTermsQueryList());
        Assertions.assertEquals(List.of("test2", "generator:docs"), query.getTerms().getTermsRequireList());
        Assertions.assertEquals(List.of("test3"), query.getTerms().getTermsPriorityList());
        Assertions.assertEquals(List.of("test4"), query.getTerms().getTermsExcludeList());

        Assertions.assertEquals(defaultLimits, query.getQueryLimits());
        Assertions.assertEquals("en", query.getLangIsoCode());
        Assertions.assertEquals(RpcSpecLimit.newBuilder().setType(RpcSpecLimit.TYPE.LESS_THAN).setValue(2015).build(), query.getYear());
        Assertions.assertEquals(RpcSpecLimit.newBuilder().setType(RpcSpecLimit.TYPE.EQUALS).setValue(10).build(), query.getRank());
        Assertions.assertEquals(RpcSpecLimit.newBuilder().setType(RpcSpecLimit.TYPE.GREATER_THAN).setValue(5).build(), query.getQuality());
        Assertions.assertEquals(RpcSpecLimit.newBuilder().setType(RpcSpecLimit.TYPE.LESS_THAN).setValue(11).build(), query.getSize());
        Assertions.assertEquals("REQUIRE_FIELD_SITE", query.getQueryStrategy());
        Assertions.assertEquals("test (test2) ?test3 -test4 year<2015 q>5 rank=10 size<11 qs=RF_SITE", query.getHumanQuery());
    }
}