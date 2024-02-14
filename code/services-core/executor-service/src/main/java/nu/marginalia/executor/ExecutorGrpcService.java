package nu.marginalia.executor;

import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;
import nu.marginalia.actor.ActorApi;
import nu.marginalia.executor.api.*;
import nu.marginalia.executor.svc.*;

public class ExecutorGrpcService extends ExecutorApiGrpc.ExecutorApiImplBase {
    private final ActorApi actorApi;
    private final ExportService exportService;
    private final SideloadService sideloadService;
    private final BackupService backupService;
    private final TransferService transferService;
    private final ProcessingService processingService;

    @Inject
    public ExecutorGrpcService(ActorApi actorApi,
                               ExportService exportService,
                               SideloadService sideloadService,
                               BackupService backupService,
                               TransferService transferService,
                               ProcessingService processingService)
    {
        this.actorApi = actorApi;
        this.exportService = exportService;
        this.sideloadService = sideloadService;
        this.backupService = backupService;
        this.transferService = transferService;
        this.processingService = processingService;
    }

    @Override
    public void startFsm(RpcFsmName request, StreamObserver<Empty> responseObserver) {
        try {
            actorApi.startActor(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
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
            responseObserver.onError(e);
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
            responseObserver.onError(e);
        }
    }

    @Override
    public void triggerCrawl(RpcFileStorageId request, StreamObserver<Empty> responseObserver) {
        try {
            processingService.startCrawl(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void triggerRecrawl(RpcFileStorageId request, StreamObserver<Empty> responseObserver) {
        try {
            processingService.startRecrawl(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void triggerConvert(RpcFileStorageId request, StreamObserver<Empty> responseObserver) {
        try {
            processingService.startConversion(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void triggerConvertAndLoad(RpcFileStorageId request, StreamObserver<Empty> responseObserver) {
        try {
            processingService.startConvertLoad(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void loadProcessedData(RpcFileStorageIds request, StreamObserver<Empty> responseObserver) {
        try {
            processingService.startLoad(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void calculateAdjacencies(Empty request, StreamObserver<Empty> responseObserver) {
        try {
            processingService.startAdjacencyCalculation();
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void sideloadEncyclopedia(RpcSideloadEncyclopedia request, StreamObserver<Empty> responseObserver) {
        try {
            sideloadService.sideloadEncyclopedia(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void sideloadDirtree(RpcSideloadDirtree request, StreamObserver<Empty> responseObserver) {
        try {
            sideloadService.sideloadDirtree(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void sideloadReddit(RpcSideloadReddit request, StreamObserver<Empty> responseObserver) {
        try {
            sideloadService.sideloadReddit(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void sideloadWarc(RpcSideloadWarc request, StreamObserver<Empty> responseObserver) {
        try {
            sideloadService.sideloadWarc(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void sideloadStackexchange(RpcSideloadStackexchange request, StreamObserver<Empty> responseObserver) {
        try {
            sideloadService.sideloadStackexchange(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void createCrawlSpecFromDownload(RpcCrawlSpecFromDownload request, StreamObserver<Empty> responseObserver) {
        try {
            processingService.createCrawlSpecFromDownload(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void exportAtags(RpcFileStorageId request, StreamObserver<Empty> responseObserver) {
        try {
            exportService.exportAtags(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void exportSampleData(RpcExportSampleData request, StreamObserver<Empty> responseObserver) {
        try {
            exportService.exportSampleData(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void exportRssFeeds(RpcFileStorageId request, StreamObserver<Empty> responseObserver) {
        try {
            exportService.exportFeeds(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void exportTermFrequencies(RpcFileStorageId request, StreamObserver<Empty> responseObserver) {
        try {
            exportService.exportTermFrequencies(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void downloadSampleData(RpcDownloadSampleData request, StreamObserver<Empty> responseObserver) {
        try {
            sideloadService.downloadSampleData(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void exportData(Empty request, StreamObserver<Empty> responseObserver) {
        try {
            exportService.exportData();
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }

    }

    @Override
    public void restoreBackup(RpcFileStorageId request, StreamObserver<Empty> responseObserver) {
        try {
            backupService.restore(request);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void getActorStates(Empty request, StreamObserver<RpcActorRunStates> responseObserver) {
        responseObserver.onNext(actorApi.getActorStates());
        responseObserver.onCompleted();
    }

    @Override
    public void listSideloadDir(Empty request, StreamObserver<RpcUploadDirContents> responseObserver) {
        try {
            responseObserver.onNext(sideloadService.listUploadDir());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void listFileStorage(RpcFileStorageId request, StreamObserver<RpcFileStorageContent> responseObserver) {
        try {
            responseObserver.onNext(transferService.listFiles(request));
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }
}
