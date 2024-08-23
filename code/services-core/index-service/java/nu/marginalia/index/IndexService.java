package nu.marginalia.index;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.IndexLocations;
import nu.marginalia.index.api.IndexMqEndpoints;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.linkgraph.DomainLinks;
import nu.marginalia.linkgraph.PartitionLinkGraphService;
import nu.marginalia.service.control.ServiceEventLog;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.service.server.SparkService;
import nu.marginalia.service.server.mq.MqRequest;
import nu.marginalia.storage.FileStorageService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static nu.marginalia.linkdb.LinkdbFileNames.DOCDB_FILE_NAME;
import static nu.marginalia.linkdb.LinkdbFileNames.DOMAIN_LINKS_FILE_NAME;

public class IndexService extends SparkService {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @NotNull
    private final Initialization init;
    private final IndexOpsService opsService;
    private final StatefulIndex statefulIndex;
    private final FileStorageService fileStorageService;
    private final DocumentDbReader documentDbReader;

    private final DomainLinks domainLinks;
    private final ServiceEventLog eventLog;


    @SneakyThrows
    @Inject
    public IndexService(BaseServiceParams params,
                        IndexOpsService opsService,
                        IndexGrpcService indexQueryService,
                        StatefulIndex statefulIndex,
                        FileStorageService fileStorageService,
                        DocumentDbReader documentDbReader,
                        DomainLinks domainLinks,
                        PartitionLinkGraphService partitionLinkGraphService,
                        ServiceEventLog eventLog)
    {
        super(params,
                ServicePartition.partition(params.configuration.node()),
                List.of(indexQueryService,
                        partitionLinkGraphService)
        );

        this.opsService = opsService;
        this.statefulIndex = statefulIndex;
        this.fileStorageService = fileStorageService;
        this.documentDbReader = documentDbReader;
        this.domainLinks = domainLinks;
        this.eventLog = eventLog;

        this.init = params.initialization;

        Thread.ofPlatform().name("initialize-index").start(this::initialize);
    }

    volatile boolean initialized = false;

    @MqRequest(endpoint = IndexMqEndpoints.INDEX_RERANK)
    public String rerank(String message) {
        if (!opsService.rerank()) {
            throw new IllegalStateException("Ops lock busy");
        }
        return "ok";
    }

    @MqRequest(endpoint = IndexMqEndpoints.INDEX_REPARTITION)
    public String repartition(String message) {
        if (!opsService.repartition()) {
            throw new IllegalStateException("Ops lock busy");
        }
        return "ok";
    }

    @SneakyThrows
    @MqRequest(endpoint = IndexMqEndpoints.SWITCH_LINKDB)
    public void switchLinkdb(String unusedArg) {
        logger.info("Switching link databases");

        Path newPathDocs = IndexLocations
                .getLinkdbWritePath(fileStorageService)
                .resolve(DOCDB_FILE_NAME);

        if (Files.exists(newPathDocs)) {
            eventLog.logEvent("INDEX-SWITCH-DOCKDB", "");
            documentDbReader.switchInput(newPathDocs);
        }

        Path newPathDomains = IndexLocations
                .getLinkdbWritePath(fileStorageService)
                .resolve(DOMAIN_LINKS_FILE_NAME);

        if (Files.exists(newPathDomains)) {
            eventLog.logEvent("INDEX-SWITCH-DOMAIN-LINKDB", "");
            domainLinks.switchInput(newPathDomains);
        }
    }

    @MqRequest(endpoint = IndexMqEndpoints.SWITCH_INDEX)
    public String switchIndex(String message) throws Exception {
        if (!opsService.switchIndex()) {
            throw new IllegalStateException("Ops lock busy");
        }

        return "ok";
    }

    @MqRequest(endpoint = IndexMqEndpoints.INDEX_IS_BLOCKED)
    public String isBlocked(String message) throws Exception {
        return Boolean.valueOf(opsService.isBusy()).toString();
    }

    public void initialize() {
        if (!initialized) {
            init.waitReady();
            statefulIndex.init();
            initialized = true;
        }
    }

}


