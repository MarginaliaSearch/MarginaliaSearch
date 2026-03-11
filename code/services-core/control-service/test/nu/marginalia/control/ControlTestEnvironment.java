package nu.marginalia.control;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.api.searchquery.QueryClient;
import nu.marginalia.control.actor.ControlActorService;
import nu.marginalia.control.node.svc.ControlCrawlDataService;
import nu.marginalia.db.DomainRankingSetsService;
import nu.marginalia.db.DomainTypes;
import nu.marginalia.executor.client.ExecutorClient;
import nu.marginalia.executor.client.ExecutorCrawlClient;
import nu.marginalia.executor.client.ExecutorExportClient;
import nu.marginalia.executor.client.ExecutorSideloadClient;
import nu.marginalia.executor.model.ActorRunStates;
import nu.marginalia.executor.upload.UploadDirContents;
import nu.marginalia.index.api.IndexMqClient;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeConfiguration;
import nu.marginalia.nodecfg.model.NodeProfile;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.renderer.config.HandlebarsConfigurator;
import nu.marginalia.screenshot.ScreenshotService;
import nu.marginalia.service.ServiceMonitors;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.storage.FileStorageService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Test environment for control-service integration tests.
 * Provides a Guice injector with mocked external dependencies
 * (gRPC clients, database, file storage) and real rendering infrastructure.
 */
public class ControlTestEnvironment {

    private final Injector injector;
    public final ControlRendererFactory controlRendererFactory;

    public ControlTestEnvironment() throws Exception {
        // Set up HikariDataSource mock chain returning empty results
        var dataSource = mock(HikariDataSource.class);
        var connection = mock(Connection.class);
        var preparedStatement = mock(PreparedStatement.class);
        var resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        // gRPC executor clients
        var executorClient = mock(ExecutorClient.class);
        when(executorClient.getActorStates(anyInt()))
                .thenReturn(new ActorRunStates(1, List.of()));
        when(executorClient.listSideloadDir(anyInt()))
                .thenReturn(new UploadDirContents("", List.of()));

        var executorCrawlClient = mock(ExecutorCrawlClient.class);
        var executorSideloadClient = mock(ExecutorSideloadClient.class);
        var executorExportClient = mock(ExecutorExportClient.class);
        var indexMqClient = mock(IndexMqClient.class);

        // Message queue factory -- createOutbox is called from ControlSysActionsService constructor
        var mqFactory = mock(MessageQueueFactory.class);
        when(mqFactory.createOutbox(anyString(), anyInt(), anyString(), anyInt(), any()))
                .thenReturn(mock(MqOutbox.class));

        // Node configuration -- getAll() returns empty list; get(n) returns a test node
        var nodeConfigurationService = mock(NodeConfigurationService.class);
        when(nodeConfigurationService.getAll()).thenReturn(List.of());
        when(nodeConfigurationService.get(anyInt())).thenReturn(
                new NodeConfiguration(1, "test-node", true, false, false,
                        false, false, NodeProfile.MIXED, false));

        // File storage service returning empty collections
        var fileStorageService = mock(FileStorageService.class);
        when(fileStorageService.getActiveFileStorages(anyInt(), any())).thenReturn(List.of());
        when(fileStorageService.getEachFileStorage(anyInt(), any())).thenReturn(List.of());
        when(fileStorageService.getStorage(anyList())).thenReturn(List.of());
        when(fileStorageService.getStorageBase(any(), anyInt())).thenReturn(null);

        var serviceMonitors = mock(ServiceMonitors.class);
        var serviceEventLog = mock(ServiceEventLog.class);
        var controlActorService = mock(ControlActorService.class);
        var mqPersistence = mock(MqPersistence.class);
        var domainTypes = mock(DomainTypes.class);

        var domainRankingSetsService = mock(DomainRankingSetsService.class);
        when(domainRankingSetsService.getAll()).thenReturn(List.of());

        var queryClient = mock(QueryClient.class);
        var screenshotService = mock(ScreenshotService.class);
        var crawlDataService = mock(ControlCrawlDataService.class);

        injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                // Real rendering infrastructure
                bind(HandlebarsConfigurator.class).to(ControlHandlebarsConfigurator.class);
                bind(ControlHandlebarsConfigurator.class).in(Singleton.class);
                bind(RendererFactory.class).in(Singleton.class);
                bind(ControlRendererFactory.class).in(Singleton.class);
                bind(RedirectControl.class).in(Singleton.class);

                // Named constant required by FileStorageService (mocked, but
                // other bindings may still require the constant)
                bindConstant().annotatedWith(Names.named("wmsa-system-node")).to(1);

                // Mocked dependencies
                bind(HikariDataSource.class).toInstance(dataSource);
                bind(ExecutorClient.class).toInstance(executorClient);
                bind(ExecutorCrawlClient.class).toInstance(executorCrawlClient);
                bind(ExecutorSideloadClient.class).toInstance(executorSideloadClient);
                bind(ExecutorExportClient.class).toInstance(executorExportClient);
                bind(IndexMqClient.class).toInstance(indexMqClient);
                bind(MessageQueueFactory.class).toInstance(mqFactory);
                bind(NodeConfigurationService.class).toInstance(nodeConfigurationService);
                bind(FileStorageService.class).toInstance(fileStorageService);
                bind(ServiceMonitors.class).toInstance(serviceMonitors);
                bind(ServiceEventLog.class).toInstance(serviceEventLog);
                bind(ControlActorService.class).toInstance(controlActorService);
                bind(MqPersistence.class).toInstance(mqPersistence);
                bind(DomainTypes.class).toInstance(domainTypes);
                bind(DomainRankingSetsService.class).toInstance(domainRankingSetsService);
                bind(QueryClient.class).toInstance(queryClient);
                bind(ScreenshotService.class).toInstance(screenshotService);
                bind(ControlCrawlDataService.class).toInstance(crawlDataService);
            }
        });

        controlRendererFactory = injector.getInstance(ControlRendererFactory.class);
    }

    public <T> T get(Class<T> clazz) {
        return injector.getInstance(clazz);
    }
}
