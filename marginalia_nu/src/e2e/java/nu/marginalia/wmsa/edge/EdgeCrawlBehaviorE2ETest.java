package nu.marginalia.wmsa.edge;


import nu.marginalia.wmsa.edge.crawling.CrawlJobExtractorMain;
import nu.marginalia.wmsa.edge.crawling.model.CrawlingSpecification;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Tag("e2e")
@Testcontainers
public class EdgeCrawlBehaviorE2ETest extends E2ETestBase {
    @Container
    public static GenericContainer<?> mockContainer = new GenericContainer<>("openjdk:17-alpine")
            .withCopyFileToContainer(jarFile(), "/WMSA.jar")
            .withNetwork(network)
            .withNetworkAliases("mock", "mock2")
            .withExposedPorts(8080)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("mock")))
            .withCommand("java","-cp","WMSA.jar","nu.marginalia.wmsa.edge.crawling.CrawlerTestMain")
    ;


    @Container
    public static GenericContainer<?> crawlerContainer = new GenericContainer<>("openjdk:17-alpine")
                .dependsOn(mockContainer)
                .withNetwork(network)
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("crawler")))
                .withFileSystemBind(modelsPath(), "/var/lib/wmsa/model", BindMode.READ_ONLY)
                .withCopyFileToContainer(ipDatabasePath(), "/var/lib/wmsa/data/IP2LOCATION-LITE-DB1.CSV")
                .withCopyFileToContainer(jarFile(), "/WMSA.jar")
                .withCopyFileToContainer(MountableFile.forClasspathResource("crawl-mock.sh"), "/crawl-mock.sh")
                .withFileSystemBind(getMockCrawlPath(), "/crawl/", BindMode.READ_WRITE)
                .withCommand("sh", "crawl-mock.sh")
                .waitingFor(Wait.forLogMessage(".*ALL DONE.*", 1).withStartupTimeout(Duration.ofMinutes(10)));


    private static String getMockCrawlPath() {
        Path crawlFiles = getCrawlPath();


        List<String> urls = new ArrayList<>();
        try {
            Files.createDirectories(crawlFiles);

            Files.writeString(crawlFiles.resolve("crawl.plan"), """
                    jobSpec: "/crawl/crawl.spec"
                    crawl:
                        dir: "/crawl/crawl"
                        logName: "crawl.log"
                    process:
                        dir: "/crawl/process"
                        logName: "process.log"
                    """);

            Files.createDirectories(crawlFiles.resolve("crawl"));
            Files.createDirectories(crawlFiles.resolve("process"));
            Files.deleteIfExists(crawlFiles.resolve("process").resolve("process.log"));
            Files.deleteIfExists(crawlFiles.resolve("crawl").resolve("crawl.log"));

            CrawlJobExtractorMain.writeSpec(crawlFiles.resolve("crawl.spec"),
                    new CrawlingSpecification("111111", 20, "mock", List.of("http://mock:8080/rate-limit/")),
                    new CrawlingSpecification("222222", 20, "mock2", List.of("http://mock2:8080/intermittent-error/")));
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
        return crawlFiles.toString();
    }


    public static MountableFile ipDatabasePath() {
        Path modelsPath = Path.of(System.getProperty("user.dir")).resolve("data/models/IP2LOC/IP2LOCATION-LITE-DB1.CSV");
        if (!Files.isRegularFile(modelsPath)) {
            System.err.println("Could not find models, looked in " + modelsPath.toAbsolutePath());
            throw new RuntimeException();
        }
        return MountableFile.forHostPath(modelsPath.toString());
    }

    private static Path getCrawlPath() {
        return Path.of(System.getProperty("user.dir")).resolve("build/tmp/crawl");
    }

    @Test
    public void testRunTheThing() throws IOException {
        // This is a test for examining the interaction between the crawler and various
        // set-ups
    }

}
