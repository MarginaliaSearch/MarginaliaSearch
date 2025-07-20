package nu.marginalia.service.server;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GrpcServer {
    private final Server server;

    private static final boolean useLoom = Boolean.getBoolean("system.experimentalUseLoom");

    public GrpcServer(ServiceConfiguration config,
                      ServiceRegistryIf serviceRegistry,
                      ServicePartition partition,
                      List<DiscoverableService> grpcServices) throws Exception {

        int port = serviceRegistry.requestPort(config.externalAddress(), new ServiceKey.Grpc<>("-", partition));

        int nThreads = Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 2, 16);

        // Start the gRPC server

        ExecutorService workExecutor = useLoom ?
                Executors.newVirtualThreadPerTaskExecutor() :
                NamedExecutorFactory.createFixed("nettyExecutor", nThreads);

        var grpcServerBuilder = NettyServerBuilder.forAddress(new InetSocketAddress(config.bindAddress(), port))
                .executor(workExecutor)
                .workerEventLoopGroup(new NioEventLoopGroup(nThreads, NamedExecutorFactory.createFixed("Worker-ELG", nThreads)))
                .bossEventLoopGroup(new NioEventLoopGroup(nThreads, NamedExecutorFactory.createFixed("Boss-ELG", nThreads)))
                .channelType(NioServerSocketChannel.class);

        for (var grpcService : grpcServices) {

            if (!grpcService.shouldRegisterService()) {
                continue;
            }

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
