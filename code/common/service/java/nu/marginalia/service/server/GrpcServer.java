package nu.marginalia.service.server;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioServerSocketChannel;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.util.NamedExecutorFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

public class GrpcServer {
    private final Server server;
    public GrpcServer(ServiceConfiguration config,
                      ServiceRegistryIf serviceRegistry,
                      ServicePartition partition,
                      List<BindableService> grpcServices) throws Exception {

        int port = serviceRegistry.requestPort(config.externalAddress(), new ServiceKey.Grpc<>("-", partition));

        int nThreads = Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 2, 16);

        // Start the gRPC server
        var grpcServerBuilder = NettyServerBuilder.forAddress(new InetSocketAddress(config.bindAddress(), port))
                .executor(NamedExecutorFactory.createFixed("nettyExecutor", nThreads))
                .workerEventLoopGroup(new NioEventLoopGroup(nThreads, NamedExecutorFactory.createFixed("Worker-ELG", nThreads)))
                .bossEventLoopGroup(new NioEventLoopGroup(nThreads, NamedExecutorFactory.createFixed("Boss-ELG", nThreads)))
                .channelType(NioServerSocketChannel.class);

        for (var grpcService : grpcServices) {
            var svc = grpcService.bindService();

            serviceRegistry.registerService(
                    ServiceKey.forServiceDescriptor(svc.getServiceDescriptor(), partition),
                    config.instanceUuid(),
                    config.externalAddress()
            );

            grpcServerBuilder.addService(svc);
        }
        server = grpcServerBuilder.build();
    }

    public void start() throws IOException {
        server.start();
    }

    public void stop() throws InterruptedException {
        server.shutdownNow();
        server.awaitTermination();
    }

}
