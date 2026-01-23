package nu.marginalia.service.server;

import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import nu.marginalia.service.discovery.ServiceRegistryIf;
import nu.marginalia.service.discovery.property.ServiceKey;
import nu.marginalia.service.discovery.property.ServicePartition;
import nu.marginalia.service.module.ServiceConfiguration;
import nu.marginalia.util.NamedExecutorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GrpcServer {
    private final Server server;
    private static final Logger logger = LoggerFactory.getLogger(GrpcServer.class);
    private static final boolean useLoom = Boolean.getBoolean("system.experimentalUseLoom");

    public GrpcServer(ServiceConfiguration config,
                      ServiceRegistryIf serviceRegistry,
                      List<DiscoverableService> grpcServices) throws Exception {

        int port = serviceRegistry.requestPort(config.externalAddress(),
                new ServiceKey.Grpc<>("-", ServicePartition.multi()));

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
                logger.info("Omitting {}", grpcService.getClass().getSimpleName());
                continue;
            }
            else {
                logger.info("Registering {}", grpcService.getClass().getSimpleName());
            }

            var svc = grpcService.bindService();

            // Register both on the node-id and wildcard partitions to allow both means of communication
            for (var partition : List.of(ServicePartition.any(), ServicePartition.partition(config.node()))) {
                serviceRegistry.registerService(
                        ServiceKey.forServiceDescriptor(svc.getServiceDescriptor(), partition),
                        config.instanceUuid(),
                        config.externalAddress()
                );
            }

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
