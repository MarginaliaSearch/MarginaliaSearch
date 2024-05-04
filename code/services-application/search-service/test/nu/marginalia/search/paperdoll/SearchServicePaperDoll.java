package nu.marginalia.search.paperdoll;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
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
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.HtmlFeature;
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

import java.net.URISyntaxException;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
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
    private static QueryResponse searchResponse;

    @SneakyThrows
    void registerSearchResult(
            String url,
            String title,
            String description,
            Collection<HtmlFeature> features,
            double quality,
            double score,
            long positions)
    {
        results.add(new DecoratedSearchResultItem(
                new SearchResultItem(url.hashCode(), 2, 3, false),
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
                null)
        );
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
                null
        );
    }

    @Test
    public void run() {
        if (!Boolean.getBoolean("runPaperDoll")) {
            return;
        }

        var injector = Guice.createInjector(
                new ServiceConfigurationModule(ServiceId.Search),
                new SearchModule(),
                this);

        injector.getInstance(SearchService.class);

        registerSearchResult("https://www.example.com/foo", "Foo", "Lorem ipsum dolor sit amet", Set.of(), 0.5, 0.5, ~0L);
        registerSearchResult("https://www.example2.com/bar", "Bar", "Some text goes here", Set.of(), 0.5, 0.5, 1L);
        registerSearchResult("https://www.example3.com/baz", "All HTML Features", "This one's got every feature", EnumSet.allOf(HtmlFeature.class), 0.5, 0.5, 1L);

        for (;;);
    }

    @SneakyThrows
    public void configure() {
        var serviceRegistry = Mockito.mock(ServiceRegistryIf.class);
        when(serviceRegistry.registerService(any(), any(), any())).thenReturn(new ServiceEndpoint("localhost", 9999));

        bind(ServiceRegistryIf.class).toInstance(serviceRegistry);
        bind(HikariDataSource.class).toInstance(dataSource);

        var qsMock = Mockito.mock(QueryClient.class);
        when(qsMock.search(any())).thenReturn(searchResponse);
        bind(QueryClient.class).toInstance(qsMock);
    }

}
