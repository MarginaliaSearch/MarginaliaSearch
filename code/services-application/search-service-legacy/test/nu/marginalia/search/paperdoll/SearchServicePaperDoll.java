package nu.marginalia.search.paperdoll;

import com.google.gson.Gson;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.api.domains.DomainInfoClient;
import nu.marginalia.api.domains.model.DomainInformation;
import nu.marginalia.api.domains.model.SimilarDomain;
import nu.marginalia.api.searchquery.QueryClient;
import nu.marginalia.api.searchquery.model.query.QueryResponse;
import nu.marginalia.api.searchquery.model.query.SearchQuery;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.api.searchquery.model.results.DecoratedSearchResultItem;
import nu.marginalia.api.searchquery.model.results.ResultRankingParameters;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.index.query.limit.QueryLimits;
import nu.marginalia.index.query.limit.QueryStrategy;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.screenshot.ScreenshotService;
import nu.marginalia.search.SearchModule;
import nu.marginalia.search.SearchService;
import nu.marginalia.service.ServiceId;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.property.ServiceEndpoint;
import nu.marginalia.service.module.ServiceConfigurationModule;
import nu.marginalia.test.TestMigrationLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import spark.Spark;

import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;


/** This class is a special test class that sets up a search service
 * and registers some search results, without actually starting the rest
 * of the environment. This is used to test the search service in isolation
 * when working on the frontend.
 * <p></p>
 * It's not actually a test, but it's in the test directory because it's
 * using test related classes.
 * <p></p>
 * When using gradle, run ./gradlew paperDoll --info to run this test,
 * the system will wait for you to kill the process to stop the test,
 * and the UI is available at port 9999.
 */
@Testcontainers
@Tag("paperdoll")
public class SearchServicePaperDoll extends AbstractModule {

    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    private static HikariDataSource dataSource;

    private static List<DecoratedSearchResultItem> results = new ArrayList<>();
    private static List<SimilarDomain> dummyLinks = new ArrayList<>();
    private static QueryResponse searchResponse;
    private static final Gson gson = GsonFactory.get();

    void registerSearchResult(
            String url,
            String title,
            String description,
            Collection<HtmlFeature> features,
            double quality,
            double score,
            long positions)
    {
        try {
            results.add(new DecoratedSearchResultItem(
                    new SearchResultItem(url.hashCode(), 2, 3, score, 0),
                    new EdgeUrl(url),
                    title,
                    description,
                    quality,
                    "HTML5",
                    HtmlFeature.encode(features),
                    null,
                    url.hashCode(),
                    400,
                    positions,
                    score,
                    4,
                    null)
            );
        }
        catch (Exception e) {
            throw new RuntimeException();
        }
    }

    @BeforeAll
    public static void setup() throws URISyntaxException {
        if (!Boolean.getBoolean("runPaperDoll")) {
            return;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mariaDBContainer.getJdbcUrl());
        config.setUsername("wmsa");
        config.setPassword("wmsa");

        dataSource = new HikariDataSource(config);

        TestMigrationLoader.flywayMigration(dataSource);

        System.setProperty("service-name", "search");
        System.setProperty("search.websiteUrl", "http://localhost:9999/");

        try (var conn = dataSource.getConnection();
             var newsStmt = conn.prepareStatement("""
                            INSERT INTO SEARCH_NEWS_FEED(TITLE, LINK, SOURCE, LIST_DATE)
                            VALUES (?, ?, ?, ?)
                            """);
             var domainStmt = conn.prepareStatement("""
                            INSERT INTO EC_DOMAIN(ID, DOMAIN_NAME, DOMAIN_TOP, NODE_AFFINITY)
                            VALUES (?, ?, ?, ?)
                            """);
             var randomStmt = conn.prepareStatement("""
                            INSERT INTO EC_RANDOM_DOMAINS(DOMAIN_ID, DOMAIN_SET)
                            VALUES (?, ?) 
                            """)
             ) {
            newsStmt.setString(1, "Lex Luthor elected president");
            newsStmt.setString(2, "https://www.example.com/foo");
            newsStmt.setString(3, "Daily Planet");
            newsStmt.setDate(4, new java.sql.Date(System.currentTimeMillis()));
            newsStmt.execute();

            newsStmt.setString(1, "Besieged Alesian onlookers confused as Caesar builds a wall around his wall around the city walls");
            newsStmt.setString(2, "https://www.example2.com/bar");
            newsStmt.setString(3, "The Gaulish Observer");
            newsStmt.setDate(4, new java.sql.Date(System.currentTimeMillis()));
            newsStmt.execute();

            newsStmt.setString(1, "Marginalia acquires Google");
            newsStmt.setString(2, "https://www.example3.com/baz");
            newsStmt.setString(3, "The Dependent");
            newsStmt.setDate(4, new java.sql.Date(System.currentTimeMillis()));
            newsStmt.execute();

            domainStmt.setInt(1, 1);
            domainStmt.setString(2, "www.example.com");
            domainStmt.setString(3, "example.com");
            domainStmt.setInt(4, 1);
            domainStmt.execute();

            domainStmt.setInt(1, 2);
            domainStmt.setString(2, "www.example2.com");
            domainStmt.setString(3, "example2.com");
            domainStmt.setInt(4, 2);
            domainStmt.execute();

            domainStmt.setInt(1, 3);
            domainStmt.setString(2, "www.example3.com");
            domainStmt.setString(3, "example3.com");
            domainStmt.setInt(4, 3);
            domainStmt.execute();

            randomStmt.setInt(1, 1);
            randomStmt.setInt(2, 0);
            randomStmt.execute();

            randomStmt.setInt(1, 2);
            randomStmt.setInt(2, 0);
            randomStmt.execute();

            randomStmt.setInt(1, 3);
            randomStmt.setInt(2, 0);
            randomStmt.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        searchResponse = new QueryResponse(
                new SearchSpecification(new SearchQuery(), List.of(), "", "test",
                        SpecificationLimit.none(),
                        SpecificationLimit.none(),
                        SpecificationLimit.none(),
                        SpecificationLimit.none(),
                        new QueryLimits(10, 20, 3, 4),
                        QueryStrategy.AUTO,
                        ResultRankingParameters.sensibleDefaults()
                        ),
                results,
                List.of(),
                List.of(),
                1,
                1,
                null
        );
    }

    @Test
    public void run() throws Exception {
        if (!Boolean.getBoolean("runPaperDoll")) {
            return;
        }

        var injector = Guice.createInjector(
                new ServiceConfigurationModule(ServiceId.Search),
                new SearchModule(),
                this);

        injector.getInstance(SearchService.class);

        List<String> suggestions = List.of("foo", "bar", "baz");

        Spark.get("/suggest/", (rq, rsp) -> {
            rsp.type("application/json");
            return gson.toJson(suggestions);
        });

        Spark.get("/screenshot/*", (rq, rsp) -> {
            rsp.type("image/svg+xml");
            return """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <svg
                   xmlns="http://www.w3.org/2000/svg"
                   width="640px"
                   height="480px"
                   viewBox="0 0 640 480"
                   version="1.1">
                  <g>
                    <rect
                       style="fill:#808080"
                       id="rect288"
                       width="595.41992"
                       height="430.01825"
                       x="23.034981"
                       y="27.850344" />
                    <text
                       xml:space="preserve"
                      style="font-size:100px;fill:#909090;font-family:sans-serif;"
                       x="20"
                       y="120">Placeholder</text>
                    <text
                       xml:space="preserve"
                       style="font-size:32px;fill:#000000;font-family:monospace;"
                       x="320" y="240" dominant-baseline="middle" text-anchor="middle">Lorem Ipsum As F</text>
                  </g>
                </svg>
                """;
        });

        registerSearchResult("https://www.example.com/foo", "Foo", "Lorem ipsum dolor sit amet", Set.of(), 0.5, 0.5, ~0L);
        registerSearchResult("https://www.example2.com/bar", "Bar", "Some text goes here", Set.of(), 0.5, 0.5, 1L);
        registerSearchResult("https://www.example3.com/baz", "All HTML Features", "This one's got every feature", EnumSet.allOf(HtmlFeature.class), 0.5, 0.5, 1L);




        dummyLinks.add(new SimilarDomain(
                new EdgeUrl("https://www.example.com/foo"),
                1,
                0.5,
                0.5,
                true,
                true,
                true,
                true,
                SimilarDomain.LinkType.FOWARD
        ));
        dummyLinks.add(new SimilarDomain(
                new EdgeUrl("https://www.example2.com/foo"),
                2,
                0.5,
                1,
                false,
                false,
                true,
                true,
                SimilarDomain.LinkType.BACKWARD
        ));
        dummyLinks.add(new SimilarDomain(
                new EdgeUrl("https://www.example3.com/foo"),
                3,
                0,
                0.5,
                false,
                false,
                false,
                false,
                SimilarDomain.LinkType.BIDIRECTIONAL
        ));


        for (;;);
    }

    public void configure() {
        try {
            var serviceRegistry = Mockito.mock(ServiceRegistryIf.class);
            when(serviceRegistry.registerService(any(), any(), any())).thenReturn(new ServiceEndpoint("localhost", 9999));

            bind(ServiceRegistryIf.class).toInstance(serviceRegistry);
            bind(HikariDataSource.class).toInstance(dataSource);

            var qsMock = Mockito.mock(QueryClient.class);
            when(qsMock.search(any())).thenReturn(searchResponse);
            bind(QueryClient.class).toInstance(qsMock);

            var asMock = Mockito.mock(DomainInfoClient.class);

            when(asMock.isAccepting()).thenReturn(true);
            when(asMock.linkedDomains(anyInt(), anyInt())).thenReturn(CompletableFuture.completedFuture(dummyLinks));
            when(asMock.similarDomains(anyInt(), anyInt())).thenReturn(CompletableFuture.completedFuture(dummyLinks));
            when(asMock.domainInformation(anyInt())).thenReturn(CompletableFuture.completedFuture(
                    new DomainInformation(new EdgeDomain("www.example.com"),
                            false,
                            123,
                            123,
                            123,
                            123,
                            123,
                            1,
                            0.5,
                            false,
                            false,
                            false,
                            "127.0.0.1",
                            1,
                            "ACME",
                            "CA",
                            "CA",
                            "Exemplary")
            ));

            bind(DomainInfoClient.class).toInstance(asMock);

            var sss = Mockito.mock(ScreenshotService.class);
            when(sss.hasScreenshot(anyInt())).thenReturn(true);
            bind(ScreenshotService.class).toInstance(sss);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
