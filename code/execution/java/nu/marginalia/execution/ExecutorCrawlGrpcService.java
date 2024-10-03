package nu.marginalia.execution;

import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorControlService;
import nu.marginalia.actor.task.ConvertActor;
import nu.marginalia.actor.task.ConvertAndLoadActor;
import nu.marginalia.actor.task.CrawlActor;
import nu.marginalia.actor.task.RecrawlSingleDomainActor;
import nu.marginalia.functions.execution.api.*;
import nu.marginalia.service.server.DiscoverableService;
import nu.marginalia.storage.model.FileStorageId;

import java.util.stream.Collectors;

public class ExecutorCrawlGrpcService
        extends ExecutorCrawlApiGrpc.ExecutorCrawlApiImplBase
        implements DiscoverableService
{
    private final ExecutorActorControlService actorControlService;

    @Inject
    public ExecutorCrawlGrpcService(ExecutorActorControlService actorControlService)
    {
        this.actorControlService = actorControlService;
    }

    @Override
    public void triggerCrawl(RpcFileStorageId request, StreamObserver<Empty> responseObserver) {
        try {
            actorControlService.startFrom(ExecutorActor.CRAWL,
                    new CrawlActor.Initial(FileStorageId.of(request.getFileStorageId()), false));

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void triggerSingleDomainRecrawl(RpcFileStorageIdWithDomainName request, StreamObserver<Empty> responseObserver) {
        try {
            actorControlService.startFrom(ExecutorActor.RECRAWL_SINGLE_DOMAIN,
                    new RecrawlSingleDomainActor.Initial(
                            FileStorageId.of(request.getFileStorageId()),
                            request.getTargetDomainName()));

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
            actorControlService.startFrom(ExecutorActor.CONVERT,
                    new ConvertActor.Convert(FileStorageId.of(request.getFileStorageId())));

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
            actorControlService.startFrom(ExecutorActor.CONVERT_AND_LOAD,
                    new ConvertAndLoadActor.Initial(FileStorageId.of(request.getFileStorageId())));

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
            actorControlService.startFrom(ExecutorActor.CONVERT_AND_LOAD,
                    new ConvertAndLoadActor.Load(request.getFileStorageIdsList()
                            .stream()
                            .map(FileStorageId::of)
                            .collect(Collectors.toList()))
            );

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

}
