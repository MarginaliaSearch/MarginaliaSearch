package nu.marginalia.search;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.LanguageModels;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.WmsaHome;
import nu.marginalia.api.domains.DomainInfoClient;
import nu.marginalia.api.feeds.FeedsClient;
import nu.marginalia.api.livecapture.LiveCaptureClient;
import nu.marginalia.api.math.MathClient;
import nu.marginalia.api.searchquery.QueryApiGrpc;
import nu.marginalia.browse.DbBrowseDomainsRandom;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.index.api.IndexMqClient;
import nu.marginalia.renderer.config.HandlebarsConfigurator;
import nu.marginalia.screenshot.ScreenshotService;
import nu.marginalia.service.client.GrpcChannelPoolFactoryIf;
import nu.marginalia.service.client.TestGrpcChannelPoolFactory;
import nu.marginalia.service.server.Initialization;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.OptionalInt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared test wiring for SearchLegacy integration and paper-doll tests.
 * Encapsulates the Guice injector and all mocked dependencies so that each
 * test class does not have to repeat the boilerplate.
 *
 * <p>The caller supplies the gRPC query-API implementation (so tests can use
 * either the simple empty-response mock or the richer paper-doll mock) and the
 * website base URL (so the paper-doll can redirect to the right port).</p>
 */
public final class SearchLegacyTestEnvironment implements AutoCloseable {

    /** Mockito mock; tests may reconfigure stubs with {@code when()} as needed. */
    public final DbDomainQueries dbDomainQueries;

    /** Mockito mock; tests may reconfigure stubs with {@code when()} as needed. */
    public final DomainBlacklist domainBlacklist;

    /** Mockito mock; tests may reconfigure stubs with {@code when()} as needed. */
    public final DbBrowseDomainsRandom dbBrowseDomainsRandom;

    /** Mockito mock; tests may reconfigure stubs with {@code when()} as needed. */
    public final ScreenshotService screenshotService;

    public final Injector injector;

    private final TestGrpcChannelPoolFactory grpcChannelPoolFactory;

    public SearchLegacyTestEnvironment(QueryApiGrpc.QueryApiImplBase queryApiImpl,
                                       WebsiteUrl websiteUrl) throws Exception {
        // Mock the database: return empty result sets for all queries so the
        // handlers can run without a real DB.
        var dataSource = mock(HikariDataSource.class);
        var conn = mock(Connection.class);
        var stmt = mock(PreparedStatement.class);
        var rs = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        // Domain info client: report unavailable so site-info short-circuits to
        // dummy values instead of making real gRPC calls.
        var domainInfoClient = mock(DomainInfoClient.class);
        when(domainInfoClient.isAccepting()).thenReturn(false);

        screenshotService = mock(ScreenshotService.class);
        when(screenshotService.hasScreenshot(any(Integer.class))).thenReturn(false);

        dbDomainQueries = mock(DbDomainQueries.class);
        when(dbDomainQueries.tryGetDomainId(any())).thenReturn(OptionalInt.empty());
        when(dbDomainQueries.getDomainId(any())).thenReturn(-1);
        when(dbDomainQueries.getDomain(any(Integer.class))).thenReturn(java.util.Optional.empty());

        domainBlacklist = mock(DomainBlacklist.class);
        when(domainBlacklist.isBlacklisted(any(Integer.class))).thenReturn(false);

        dbBrowseDomainsRandom = mock(DbBrowseDomainsRandom.class);
        when(dbBrowseDomainsRandom.getRandomDomains(any(Integer.class), any(), any(Integer.class)))
                .thenReturn(List.of());

        grpcChannelPoolFactory = new TestGrpcChannelPoolFactory(List.of(queryApiImpl));

        injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(HikariDataSource.class).toInstance(dataSource);
                bind(GrpcChannelPoolFactoryIf.class).toInstance(grpcChannelPoolFactory);
                bind(Initialization.class).toInstance(new Initialization());
                bind(DomainInfoClient.class).toInstance(domainInfoClient);
                bind(MathClient.class).toInstance(mock(MathClient.class));
                bind(FeedsClient.class).toInstance(mock(FeedsClient.class));
                bind(LiveCaptureClient.class).toInstance(mock(LiveCaptureClient.class));
                bind(IndexMqClient.class).toInstance(mock(IndexMqClient.class));
                bind(ScreenshotService.class).toInstance(screenshotService);
                bind(DbDomainQueries.class).toInstance(dbDomainQueries);
                bind(DomainBlacklist.class).toInstance(domainBlacklist);
                bind(DbBrowseDomainsRandom.class).toInstance(dbBrowseDomainsRandom);
                bind(WebsiteUrl.class).toInstance(websiteUrl);
                bind(HandlebarsConfigurator.class).to(SearchHandlebarsConfigurator.class);
                bind(LanguageModels.class).toInstance(WmsaHome.getLanguageModels());
            }
        });
    }

    @Override
    public void close() {
        grpcChannelPoolFactory.close();
    }
}
