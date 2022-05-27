package nu.marginalia.wmsa.edge;

import nu.marginalia.wmsa.configuration.ServiceDescriptor;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public abstract class E2ETestBase {
    public Network network = Network.newNetwork();

    public GenericContainer<?> getMariaDBContainer() {
        return new MariaDBContainer<>("mariadb")
                .withDatabaseName("WMSA_prod")
                .withUsername("wmsa")
                .withPassword("wmsa")
                .withInitScript("sql/edge-crawler-cache.sql")
                .withNetwork(network)
                .withNetworkAliases("mariadb");
    }

    public GenericContainer<?> forService(ServiceDescriptor service, GenericContainer<?> mariaDB) {
        return new GenericContainer<>("openjdk:17-alpine")
                .dependsOn(mariaDB)
                .withCopyFileToContainer(jarFile(), "/WMSA.jar")
                .withCopyFileToContainer(MountableFile.forClasspathResource("init.sh"), "/init.sh")
                .withExposedPorts(service.port)
                .withFileSystemBind(modelsPath(), "/var/lib/wmsa/model", BindMode.READ_ONLY)
                .withNetwork(network)
                .withNetworkAliases(service.name)
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(service.name)))
                .withCommand("sh", "init.sh", service.name)
                .waitingFor(Wait.forHttp("/internal/ping")
                        .forPort(service.port)
                        .withReadTimeout(Duration.ofSeconds(15)))
                ;
    }

    public static MountableFile jarFile() {
        Path cwd = Path.of(System.getProperty("user.dir"));

        cwd = cwd.resolve("..");
        var jarFile =  cwd.resolve("build/libs/wmsa-SNAPSHOT-all.jar");
        if (!Files.exists(jarFile)) {
            System.err.println("Could not find jarFile " + jarFile);
            throw new RuntimeException();
        }
        else {
            System.out.println("jar file = " + jarFile);
        }
        return MountableFile.forHostPath(jarFile);
    }

    public static String modelsPath() {
        Path modelsPath = Path.of(System.getProperty("user.dir")).resolve("data/models");
        if (!Files.isDirectory(modelsPath)) {
            System.err.println("Could not find models, looked in " + modelsPath.toAbsolutePath());
            throw new RuntimeException();
        }
        return modelsPath.toString();
    }
}
