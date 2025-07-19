package nu.marginalia.execution;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorControlService;
import nu.marginalia.actor.precession.ExportAllPrecessionActor;
import nu.marginalia.actor.task.*;
import nu.marginalia.functions.execution.api.*;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.service.server.DiscoverableService;
import nu.marginalia.storage.model.FileStorageId;

@Singleton
public class ExecutorExportGrpcService
        extends ExecutorExportApiGrpc.ExecutorExportApiImplBase
        implements DiscoverableService
{
    private final ExecutorActorControlService actorControlService;
    private final ServiceConfiguration serviceConfiguration;

    @Inject
    public ExecutorExportGrpcService(ExecutorActorControlService actorControlService, ServiceConfiguration serviceConfiguration) {
        this.actorControlService = actorControlService;
        this.serviceConfiguration = serviceConfiguration;
    }

    @Override
    public void exportAtags(RpcExportRequest request, StreamObserver<Empty> responseObserver) {
        try {
            actorControlService.startFrom(ExecutorActor.EXPORT_ATAGS,
                    new ExportAtagsActor.Export(
                            request.getMsgId(),
                            FileStorageId.of(request.getFileStorageId()))
            );
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void exportSampleData(RpcExportSampleData request, StreamObserver<Empty> responseObserver) {
        try {
            actorControlService.startFrom(ExecutorActor.EXPORT_SAMPLE_DATA,
                    new ExportSampleDataActor.Export(
                            FileStorageId.of(request.getFileStorageId()),
                            request.getSize(),
                            request.getCtFilter(),
                            request.getName()
                    )
            );
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void exportRssFeeds(RpcExportRequest request, StreamObserver<Empty> responseObserver) {
        try {
            actorControlService.startFrom(ExecutorActor.EXPORT_FEEDS,
                    new ExportFeedsActor.Export(
                            request.getMsgId(),
                            FileStorageId.of(request.getFileStorageId()))
            );
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void exportTermFrequencies(RpcExportRequest request, StreamObserver<Empty> responseObserver) {
        try {
            actorControlService.startFrom(ExecutorActor.EXPORT_TERM_FREQUENCIES,
                    new ExportTermFreqActor.Export(request.getMsgId(), FileStorageId.of(request.getFileStorageId()))
            );
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
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
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
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
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void exportAllAtags(Empty request, StreamObserver<Empty> responseObserver) {
        if (serviceConfiguration.node() != 1) {
            responseObserver.onError(Status.UNAVAILABLE.withDescription("Export all atags is only available on node 1").asRuntimeException());
        }
        try {
            actorControlService.startFrom(ExecutorActor.PREC_EXPORT_ALL,
                    new ExportAllPrecessionActor.Initial(ExportAllPrecessionActor.ExportTask.ATAGS)
            );
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void exportAllFeeds(Empty request, StreamObserver<Empty> responseObserver) {
        try {
            actorControlService.startFrom(ExecutorActor.PREC_EXPORT_ALL,
                    new ExportAllPrecessionActor.Initial(ExportAllPrecessionActor.ExportTask.FEEDS)
            );
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void exportAllTfreqs(Empty request, StreamObserver<Empty> responseObserver) {
        try {
            actorControlService.startFrom(ExecutorActor.PREC_EXPORT_ALL,
                    new ExportAllPrecessionActor.Initial(ExportAllPrecessionActor.ExportTask.TFREQ)
            );
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }
}
