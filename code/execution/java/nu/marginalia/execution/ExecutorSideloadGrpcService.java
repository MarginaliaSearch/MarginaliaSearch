package nu.marginalia.execution;

import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;
import nu.marginalia.actor.ExecutorActor;
import nu.marginalia.actor.ExecutorActorControlService;
import nu.marginalia.actor.task.ConvertActor;
import nu.marginalia.functions.execution.api.*;
import nu.marginalia.service.server.DiscoverableService;

public class ExecutorSideloadGrpcService
        extends ExecutorSideloadApiGrpc.ExecutorSideloadApiImplBase
        implements DiscoverableService
{
    private final ExecutorActorControlService actorControlService;

    @Inject
    public ExecutorSideloadGrpcService(ExecutorActorControlService actorControlService)
    {
        this.actorControlService = actorControlService;
    }

    @Override
    public void sideloadEncyclopedia(RpcSideloadEncyclopedia request, StreamObserver<Empty> responseObserver) {
        try {
            actorControlService.startFrom(ExecutorActor.CONVERT,
                    new ConvertActor.ConvertEncyclopedia(
                            request.getSourcePath(),
                            request.getBaseUrl()
                    ));

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
            actorControlService.startFrom(ExecutorActor.CONVERT,
                    new ConvertActor.ConvertDirtree(request.getSourcePath())
            );

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
            actorControlService.startFrom(ExecutorActor.CONVERT,
                    new ConvertActor.ConvertReddit(request.getSourcePath())
            );

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
            actorControlService.startFrom(ExecutorActor.CONVERT,
                    new ConvertActor.ConvertWarc(request.getSourcePath())
            );

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
            actorControlService.startFrom(ExecutorActor.CONVERT,
                    new ConvertActor.ConvertStackexchange(request.getSourcePath())
            );

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(e);
        }
    }

}
