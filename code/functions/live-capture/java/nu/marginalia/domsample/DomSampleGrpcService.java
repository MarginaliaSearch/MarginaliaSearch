package nu.marginalia.domsample;

import com.google.inject.Inject;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import nu.marginalia.api.domsample.*;
import nu.marginalia.domsample.db.DomSampleDb;
import nu.marginalia.service.server.DiscoverableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DomSampleGrpcService
        extends DomSampleApiGrpc.DomSampleApiImplBase
        implements DiscoverableService
{
    private static final Logger logger = LoggerFactory.getLogger(DomSampleGrpcService.class);

    private final DomSampleDb domSampleDb;

    @Inject
    public DomSampleGrpcService(DomSampleDb domSampleDb) {
        this.domSampleDb = domSampleDb;
    }

    @Override
    public void getSample(RpcDomainName request, StreamObserver<RpcDomainSample> responseObserver) {
        String domainName = request.getDomainName();
        if (domainName.isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("Invalid domain name")
                            .asRuntimeException());
            return;
        }

        try {
            List<DomSampleDb.Sample> dbRecords = domSampleDb.getSamples(domainName);
            if (dbRecords.isEmpty()) {
                responseObserver.onError(Status.NOT_FOUND.withDescription("No sample found").asRuntimeException());
                return;
            }

            // Grab the first sample
            RpcDomainSample.Builder response = convert(dbRecords.getFirst());

            responseObserver.onNext(response.build());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            logger.error("Error in getSample()", e);
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void hasSample(RpcDomainName request, StreamObserver<RpcBooleanRsp> responseObserver) {
        String domainName = request.getDomainName();
        if (domainName.isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid domain name")
                    .asRuntimeException());
            return;
        }

        try {
            responseObserver.onNext(RpcBooleanRsp.newBuilder()
                    .setAnswer(domSampleDb.hasSample(domainName)).build());
            responseObserver.onCompleted();
        }
        catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    @Override
    public void getAllSamples(RpcDomainName request, StreamObserver<RpcDomainSample> responseObserver) {
        String domainName = request.getDomainName();
        if (domainName.isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid domain name")
                    .asRuntimeException());
            return;
        }

        try {
            List<DomSampleDb.Sample> dbRecords = domSampleDb.getSamples(domainName);

            for (var record : dbRecords) {
                responseObserver.onNext(convert(record).build());
            }

            responseObserver.onCompleted();
        }
        catch (Exception e) {
            logger.error("Error in getSample()", e);
            responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
        }
    }

    private RpcDomainSample.Builder convert(DomSampleDb.Sample dbSample) {

        var sampleBuilder = RpcDomainSample.newBuilder()
                .setDomainName(dbSample.domain())
                .setAcceptedPopover(dbSample.acceptedPopover())
                .setHtmlSample(dbSample.sample());

        for (var req : dbSample.parseRequests()) {
            sampleBuilder.addOutgoingRequestsBuilder()
                    .setUrl(req.uri().toString())
                    .setMethod(switch (req.method().toUpperCase())
                    {
                        case "GET" -> RpcOutgoingRequest.RequestMethod.GET;
                        case "POST" -> RpcOutgoingRequest.RequestMethod.POST;
                        default -> RpcOutgoingRequest.RequestMethod.OTHER;
                    })
                    .setTimestamp(req.timestamp());
        }

        return sampleBuilder;
    }

}
