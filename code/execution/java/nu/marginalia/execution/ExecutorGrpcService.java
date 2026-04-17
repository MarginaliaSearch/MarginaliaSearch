package nu.marginalia.execution;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.netty.handler.codec.HeadersUtils;
import nu.marginalia.WmsaHome;
import nu.marginalia.actor.ActorApi;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorControlService;
import nu.marginalia.actor.state.ActorStateInstance;
import nu.marginalia.actor.task.DownloadSampleActor;
import nu.marginalia.actor.task.RestoreBackupActor;
import nu.marginalia.actor.task.TriggerAdjacencyCalculationActor;
import nu.marginalia.actor.task.UpdateNsfwFiltersActor;
import nu.marginalia.functions.execution.api.*;
import nu.marginalia.model.crawldata.SerializableCrawlData;
import nu.marginalia.ping.fetcher.response.Headers;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.service.server.DiscoverableService;
import nu.marginalia.slop.SlopCrawlDataRecord;
import nu.marginalia.slop.SlopTable;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpHeaders;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static io.grpc.stub.ClientCalls.futureUnaryCall;

public class ExecutorGrpcService
        extends ExecutorApiGrpc.ExecutorApiImplBase
        implements DiscoverableService
{
    private final ActorApi actorApi;
    private final FileStorageService fileStorageService;
    private final ServiceConfiguration serviceConfiguration;
    private final ExecutorActorControlService actorControlService;

    private static final Logger logger = LoggerFactory.getLogger(ExecutorGrpcService.class);

    @Inject
    public ExecutorGrpcService(ActorApi actorApi,
                               FileStorageService fileStorageService,
                               ServiceConfiguration serviceConfiguration,
                               ExecutorActorControlService actorControlService)
    {
        this.actorApi = actorApi;
        this.fileStorageService = fileStorageService;
        this.serviceConfiguration = serviceConfiguration;
        this.actorControlService = actorControlService;
    }

    @Override
    public void startFsm(RpcFsmName request, StreamObserver<Empty> responseObserver) {
        try {
            actorApi.startActor(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void stopFsm(RpcFsmName request, StreamObserver<Empty> responseObserver) {
        try {
            actorApi.stopActor(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void stopProcess(RpcProcessId request, StreamObserver<Empty> responseObserver) {
        try {
            actorApi.stopProcess(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void calculateAdjacencies(Empty request, StreamObserver<Empty> responseObserver) {
        try {
            actorControlService.startFrom(ExecutorActor.ADJACENCY_CALCULATION,
                    new TriggerAdjacencyCalculationActor.Run());

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void downloadSampleData(RpcDownloadSampleData request, StreamObserver<Empty> responseObserver) {
        try {
            String sampleSet = request.getSampleSet();

            actorControlService.startFrom(ExecutorActor.DOWNLOAD_SAMPLE,
                    new DownloadSampleActor.Run(sampleSet));

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void restoreBackup(RpcFileStorageId request, StreamObserver<Empty> responseObserver) {
        try {
            var fid = FileStorageId.of(request.getFileStorageId());

            actorControlService.startFrom(ExecutorActor.RESTORE_BACKUP,
                    new RestoreBackupActor.Restore(fid));

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void getActorStates(Empty request, StreamObserver<RpcActorRunStates> responseObserver) {
        var items = actorControlService.getActorStates().entrySet().stream().map(e -> {
                    final var stateGraph = actorControlService.getActorDefinition(e.getKey());

                    final ActorStateInstance state = e.getValue();
                    final String actorDescription = stateGraph.describe();

                    final String machineName = e.getKey().name();
                    final String stateName = state.name();

                    final String stateDescription = "";

                    final boolean terminal = state.isFinal();
                    final boolean canStart = actorControlService.isDirectlyInitializable(e.getKey()) && terminal;

                    return RpcActorRunState
                            .newBuilder()
                            .setActorName(machineName)
                            .setState(stateName)
                            .setActorDescription(actorDescription)
                            .setStateDescription(stateDescription)
                            .setTerminal(terminal)
                            .setCanStart(canStart)
                            .build();

                })
                .filter(s -> !s.getTerminal() || s.getCanStart())
                .sorted(Comparator.comparing(RpcActorRunState::getActorName))
                .toList();

        responseObserver.onNext(RpcActorRunStates.newBuilder()
                .setNode(serviceConfiguration.node())
                .addAllActorRunStates(items)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void listSideloadDir(Empty request, StreamObserver<RpcUploadDirContents> responseObserver) {
        try {
            Path uploadDir = WmsaHome.getUploadDir();

            try (var items = Files.list(uploadDir).sorted(
                    Comparator.comparing((Path d) -> Files.isDirectory(d)).reversed()
                            .thenComparing(path -> path.getFileName().toString())
            )) {
                var builder = RpcUploadDirContents.newBuilder().setPath(uploadDir.toString());

                var iter = items.iterator();
                while (iter.hasNext()) {
                    var path = iter.next();

                    boolean isDir = Files.isDirectory(path);
                    long size = isDir ? 0 : Files.size(path);
                    var mtime = Files.getLastModifiedTime(path);

                    builder.addEntriesBuilder()
                            .setName(path.toString())
                            .setIsDirectory(isDir)
                            .setLastModifiedTime(
                                    LocalDateTime.ofInstant(mtime.toInstant(), ZoneId.systemDefault()).format(DateTimeFormatter.ISO_DATE_TIME))
                            .setSize(size);
                }

                responseObserver.onNext(builder.build());
            }

            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void listFileStorage(RpcFileStorageId request, StreamObserver<RpcFileStorageContent> responseObserver) {
        try {
            FileStorageId fileStorageId = FileStorageId.of(request.getFileStorageId());

            var storage = fileStorageService.getStorage(fileStorageId);

            var builder = RpcFileStorageContent.newBuilder();


            try (var fs = Files.list(storage.asPath())) {
                fs.filter(Files::isRegularFile)
                        .map(this::createFileModel)
                        .sorted(Comparator.comparing(RpcFileStorageEntry::getName))
                        .forEach(builder::addEntries);
            }

            responseObserver.onNext(builder.build());

            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    private RpcFileStorageEntry createFileModel(Path path) {
        try {
            return RpcFileStorageEntry.newBuilder()
                    .setName(path.toFile().getName())
                    .setSize(Files.size(path))
                    .setLastModifiedTime(Files.getLastModifiedTime(path).toInstant().toString())
                    .build();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void restartExecutorService(Empty request, StreamObserver<Empty> responseObserver) {
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();

        logger.info("Restarting executor service on node {}", serviceConfiguration.node());

        try {
            // Wait for the response to be sent before restarting
            Thread.sleep(Duration.ofSeconds(5));
        }
        catch (InterruptedException e) {
            logger.warn("Interrupted while waiting for restart", e);
        }

        System.exit(0);
    }

    @Override
    public void updateNsfwFilters(RpcUpdateNsfwFilters request, StreamObserver<Empty> responseObserver) {
        logger.info("Got request {}", request);
        try {
            actorControlService.startFrom(ExecutorActor.UPDATE_NSFW_LISTS,
                    new UpdateNsfwFiltersActor.Initial(request.getMsgId()));

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            logger.error("Failed to update nsfw filters", e);
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void fetchCrawlDataSample(RpcCrawlDataSampleReq request,
                                     StreamObserver<RpcCrawlDataSampleRsp> responseObserver)
    {
        Map<String, Integer> countByContentType = new HashMap<>();
        Map<Integer, Integer> countByStatusCode = new HashMap<>();
        List<RpcCrawlDataSampleRecord> records = new ArrayList<>();

        System.out.println(request);

        var rspBuilder = RpcCrawlDataSampleRsp.newBuilder();

        try {
            Path slopDataPath = fileStorageService.getStorage(FileStorageId.of(request.getFileStorageId()))
                    .asPath().resolve(request.getPath());

            try (SlopTable slopTable = new SlopTable(slopDataPath)) {
                var domainReader = SlopCrawlDataRecord.domainColumn.open(slopTable);
                if (domainReader.hasRemaining()) {
                    rspBuilder.setDomain(domainReader.get());
                }
            }

            try (SlopTable slopTable = new SlopTable(slopDataPath)) {
                var contentTypeReader = SlopCrawlDataRecord.contentTypeColumn.open(slopTable);
                var statusCodeReader = SlopCrawlDataRecord.statusColumn.open(slopTable);

                while (contentTypeReader.hasRemaining()) {
                    countByContentType.merge(contentTypeReader.get(), 1, Integer::sum);
                    countByStatusCode.merge((int) statusCodeReader.get(), 1, Integer::sum);
                }
            }

            countByContentType.forEach((code, cnt) -> {
                rspBuilder.addByContentType(RpcCrawlDataSamplesByContentType.newBuilder().setContentType(code).setCount(cnt));
            });

            countByStatusCode.forEach((code, cnt) -> {
                rspBuilder.addByStatusCode(RpcCrawlDataSamplesByStatusCode.newBuilder().setStatusCode(code).setCount(cnt));
            });

            try (SlopTable slopTable = new SlopTable(slopDataPath)) {
                var urlReader = SlopCrawlDataRecord.urlColumn.open(slopTable);
                var bodyReader = SlopCrawlDataRecord.bodyColumn.open(slopTable);
                var headerReader = SlopCrawlDataRecord.headerColumn.open(slopTable);
                var contentTypeReader = SlopCrawlDataRecord.contentTypeColumn.open(slopTable);
                var statusCodeReader = SlopCrawlDataRecord.statusColumn.open(slopTable);


                int toSkip = request.getAfter();
                int toFetch = 10;

                while (contentTypeReader.hasRemaining()) {
                    String url = urlReader.get();
                    String contentType = contentTypeReader.get();
                    int statusCode = statusCodeReader.get();

                    if (!Strings.isNullOrEmpty(request.getUrlGlob()) && !matchesGlob(request.getUrlGlob(), url))
                        continue;
                    if (!Strings.isNullOrEmpty(request.getContentType()) && !contentType.equalsIgnoreCase(request.getContentType()))
                        continue;
                    if (request.getHttpStatus() > 0 && statusCode != request.getHttpStatus())
                        continue;

                    if (toSkip > 0) {
                        toSkip--;
                        continue;
                    }

                    // Bring the other readers into alignment
                    bodyReader.prealign(urlReader);
                    headerReader.prealign(urlReader);

                    boolean hasBody = bodyReader.get().length > 0;

                    Map<String, String> headers = Arrays.stream(StringUtils.split(headerReader.get(), "\n"))
                                    .map(s -> StringUtils.split(s, ":"))
                                    .filter(parts -> parts.length == 2)
                                    .collect(Collectors.toMap(parts -> parts[0].trim(), parts -> parts[1].trim(), (a,b)->a));

                    rspBuilder.addRecords(RpcCrawlDataSampleRecord.newBuilder()
                            .setContentType(contentType)
                            .setHttpStatus(statusCode)
                            .setHasBody(hasBody)
                            .setUrl(url)
                            .setEtag(headers.getOrDefault("ETag", ""))
                            .setLastModified(headers.getOrDefault("Last-Modified", ""))
                            .build()
                    );

                    if (--toFetch < 0)
                        break;
                }

                slopTable.alignAll(contentTypeReader);
            }

            System.out.println(rspBuilder);

            responseObserver.onNext(rspBuilder.build());
            responseObserver.onCompleted();
        }
        catch (IOException | SQLException ex) {
            logger.error("Error fetching crawl data", ex);
            responseObserver.onError(Status.INTERNAL.withCause(ex).asRuntimeException());
        }
    }

    private boolean matchesGlob(String pattern, String input) {
        if (!pattern.contains("*")) return pattern.equals(input);

        String[] parts = StringUtils.splitPreserveAllTokens(pattern, "*");
        int pos = 0;

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (i == 0) {
                if (!input.startsWith(part)) return false;
                pos = part.length();
            } else if (i == parts.length - 1) {
                if (!input.endsWith(part)) return false;
            } else {
                int idx = input.indexOf(part, pos);
                if (idx < 0) return false;
                pos = idx + part.length();
            }
        }

        return true;
    }
}
