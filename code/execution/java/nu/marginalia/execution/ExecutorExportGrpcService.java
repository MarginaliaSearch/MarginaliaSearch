package nu.marginalia.execution;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.stub.StreamObserver;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorControlService;
import nu.marginalia.actor.task.*;
import nu.marginalia.functions.execution.api.*;
import nu.marginalia.storage.model.FileStorageId;

import java.nio.file.Path;

@Singleton
public class ExecutorExportGrpcService extends ExecutorExportApiGrpc.ExecutorExportApiImplBase {
    private final ExecutorActorControlService actorControlService;

    @Inject
    public ExecutorExportGrpcService(ExecutorActorControlService actorControlService) {
        this.actorControlService = actorControlService;
    }

    @Override
    public void exportAtags(RpcFileStorageId request, StreamObserver<Empty> responseObserver) {
        try {
            actorControlService.startFrom(ExecutorActor.EXPORT_ATAGS,
                    new ExportAtagsActor.Export(FileStorageId.of(request.getFileStorageId()))
            );
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
            actorControlService.startFrom(ExecutorActor.EXPORT_SAMPLE_DATA,
                    new ExportSampleDataActor.Export(
                            FileStorageId.of(request.getFileStorageId()),
                            request.getSize(),
                            request.getName()
                    )
            );
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
            actorControlService.startFrom(ExecutorActor.EXPORT_FEEDS,
                    new ExportFeedsActor.Export(FileStorageId.of(request.getFileStorageId()))
            );
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
            actorControlService.startFrom(ExecutorActor.EXPORT_TERM_FREQUENCIES,
                    new ExportTermFreqActor.Export(FileStorageId.of(request.getFileStorageId()))
            );
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
            actorControlService.startFrom(ExecutorActor.EXPORT_DATA, new ExportDataActor.Export());
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void exportSegmentationModel(RpcExportSegmentationModel request, StreamObserver<Empty> responseObserver) {
        try {
            actorControlService.startFrom(ExecutorActor.EXPORT_SEGMENTATION_MODEL,
                    new ExportSegmentationModelActor.Export(request.getSourcePath())
            );

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

}
