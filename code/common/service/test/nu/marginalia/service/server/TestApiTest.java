package nu.marginalia.service.server;

import io.grpc.stub.StreamObserver;
import nu.marginalia.service.client.TestGrpcChannelPoolFactory;
import nu.marginalia.test.RpcInteger;
import nu.marginalia.test.TestApiGrpc;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;


public class TestApiTest {
    @Test
    public void testTestTest() throws IOException {
        var cf = new TestGrpcChannelPoolFactory(List.of(
                new TestApiGrpc.TestApiImplBase() {
                    public void increment(RpcInteger request,
                                          StreamObserver<RpcInteger> responseObserver) {
                        responseObserver.onNext(RpcInteger.newBuilder().setValue(request.getValue()+1).build());
                        responseObserver.onCompleted();
                    }
                }
        ));

        System.out.println(cf.createSingle(null, TestApiGrpc::newBlockingStub).call(
                TestApiGrpc.TestApiBlockingStub::increment
        ).run(RpcInteger.newBuilder().setValue(5).build()));

        System.out.println(cf.createMulti(null, TestApiGrpc::newBlockingStub).call(
                TestApiGrpc.TestApiBlockingStub::increment
        ).run(RpcInteger.newBuilder().setValue(5).build()));

        cf.close();
    }
}
