package nu.marginalia.wmsa.edge;


import nu.marginalia.util.test.TestUtil;
import nu.marginalia.wmsa.edge.crawling.CrawlJobExtractorMain;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openzim.ZIMTypes.ZIMFile;
import org.openzim.ZIMTypes.ZIMReader;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.NginxContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static nu.marginalia.wmsa.configuration.ServiceDescriptor.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("e2e")
@Testcontainers
public class EdgeSearchE2ETest extends E2ETestBase {
    @Container
    public static GenericContainer<?> mariaDB = getMariaDBContainer();

    @Container
    public static GenericContainer<?> searchContainer =  forService(EDGE_SEARCH, mariaDB);
    @Container
    public static GenericContainer<?> assistantContainer =  forService(EDGE_ASSISTANT, mariaDB);
    @Container
    public static GenericContainer<?> indexContainer =  forService(EDGE_INDEX, mariaDB);

    @Container
    public static NginxContainer<?> mockWikipedia = new NginxContainer<>("nginx:stable")
            .dependsOn(searchContainer)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("wikipedia")))
            .withFileSystemBind(getWikipediaFiles(), "/usr/share/nginx/html/", BindMode.READ_ONLY)
            .withNetwork(network)
            .withNetworkAliases("wikipedia.local");


    @Container
    public static  BrowserWebDriverContainer<?> chrome = new BrowserWebDriverContainer<>()
            .withNetwork(network)
            .withCapabilities(new ChromeOptions());

    @Container
    public static GenericContainer<?> crawlerContainer = new GenericContainer<>("openjdk:17-alpine")
                .dependsOn(mockWikipedia)
                .dependsOn(indexContainer)
                .withNetwork(network)
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("crawler")))
                .withFileSystemBind(modelsPath(), "/var/lib/wmsa/model", BindMode.READ_ONLY)
                .withCopyFileToContainer(ipDatabasePath(), "/var/lib/wmsa/data/IP2LOCATION-LITE-DB1.CSV")
                .withCopyFileToContainer(jarFile(), "/WMSA.jar")
                .withCopyFileToContainer(MountableFile.forClasspathResource("crawl.sh"), "/crawl.sh")
                .withFileSystemBind(getCrawlPath().toString(), "/crawl/", BindMode.READ_WRITE)
                .withCommand("sh", "crawl.sh")
                .waitingFor(Wait.forLogMessage(".*ALL DONE.*", 1).withStartupTimeout(Duration.ofMinutes(10)));

    @Container
    public static  NginxContainer<?> proxyNginx = new NginxContainer<>("nginx:stable")
            .dependsOn(searchContainer)
            .dependsOn(crawlerContainer)
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("nginx")))
            .withCopyFileToContainer(MountableFile.forClasspathResource("nginx/search.conf"), "/etc/nginx/conf.d/default.conf")
            .withNetwork(network)
            .withNetworkAliases("proxyNginx");

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

    private static Path screenshotFilename(String operation) throws IOException {
        var path = Path.of(System.getProperty("user.dir")).resolve("build/test/e2e/");
        Files.createDirectories(path);

        String name = String.format("test-%s-%s.png", operation, LocalDateTime.now());
        path = path.resolve(name);

        System.out.println("Screenshot in " + path);
        return path;
    }

    private static String getWikipediaFiles() {
        Path wikipediaFiles = Path.of(System.getProperty("user.dir")).resolve("build/tmp/wikipedia");
        Path crawlFiles = getCrawlPath();
        Path zimFile = Path.of(System.getProperty("user.dir")).resolve("data/test/wikipedia_en_100_nopic.zim");


        List<String> urls = new ArrayList<>();
        try {
            TestUtil.clearTempDir(wikipediaFiles);
            Files.createDirectories(wikipediaFiles);
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

            var zr = new ZIMReader(new ZIMFile(zimFile.toString()));
            zr.forEachArticles((url, art) -> {
                urls.add("http://wikipedia.local/" + url + ".html");

                if (art != null) {
                    try {
                        var doc = Jsoup.parse(art);
                        doc.getElementsByTag("script").remove();
                        Files.writeString(wikipediaFiles.resolve(url+".html"), doc.html());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }, pred -> true);
            urls.forEach(System.out::println);
            Files.writeString(wikipediaFiles.resolve("index.html"), "<html/>");
            CrawlJobExtractorMain.writeSpec(crawlFiles.resolve("crawl.spec"), "wikipedia.local", urls);
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
        return wikipediaFiles.toString();
    }

    private List<String> getTitlesFromSearchResults(String html) {
        List<String> ret = new ArrayList<>();

        for (var title : Jsoup.parse(html).select(".card.search-result > h2")) {
            ret.add(title.text());
        }

        return ret;
    }

    @Test
    public void testFrontPage() throws IOException {
        var driver = chrome.getWebDriver();

        driver.get("http://proxyNginx/");
        System.out.println(driver.getTitle());
        System.out.println(driver.findElement(new By.ByXPath("//*")).getAttribute("outerHTML"));

        Files.move(driver.getScreenshotAs(OutputType.FILE).toPath(), screenshotFilename("frontpage"));
    }

    @Test
    public void testQuery() throws IOException {
        var driver = chrome.getWebDriver();

        driver.get("http://proxyNginx/search?query=bird&profile=corpo");
        System.out.println(driver.getTitle());

        var html = driver.findElement(new By.ByXPath("//*")).getAttribute("outerHTML");
        assertEquals(List.of("Bird"), getTitlesFromSearchResults(html));

        Files.move(driver.getScreenshotAs(OutputType.FILE).toPath(), screenshotFilename("query"));
    }

    @Test
    public void testSiteInfo() throws IOException {
        var driver = chrome.getWebDriver();

        driver.get("http://proxyNginx/search?query=site:wikipedia.local");
        System.out.println(driver.getTitle());
        System.out.println(driver.findElement(new By.ByXPath("//*")).getAttribute("outerHTML"));

        Files.move(driver.getScreenshotAs(OutputType.FILE).toPath(), screenshotFilename("site-info"));
    }

    @Test
    public void testSiteSearch() throws IOException {
        var driver = chrome.getWebDriver();

        driver.get("http://proxyNginx/search?query=site:wikipedia.local%20frog");
        System.out.println(driver.getTitle());

        var html = driver.findElement(new By.ByXPath("//*")).getAttribute("outerHTML");

        Files.move(driver.getScreenshotAs(OutputType.FILE).toPath(), screenshotFilename("site-search"));

        assertEquals(List.of("Frog", "Binomial nomenclature", "Mantis", "Amphibian"), getTitlesFromSearchResults(html));

    }

    @Test
    public void testBrowse() throws IOException {
        var driver = chrome.getWebDriver();

        driver.get("http://proxyNginx/search?query=browse:wikipedia.local");
        System.out.println(driver.getTitle());
        System.out.println(driver.findElement(new By.ByXPath("//*")).getAttribute("outerHTML"));

        Files.move(driver.getScreenshotAs(OutputType.FILE).toPath(), screenshotFilename("browse"));
    }
    @Test
    public void testDefine() throws IOException {
        var driver = chrome.getWebDriver();

        driver.get("http://proxyNginx/search?query=define:adiabatic");
        System.out.println(driver.getTitle());
        System.out.println(driver.findElement(new By.ByXPath("//*")).getAttribute("outerHTML"));

        Files.move(driver.getScreenshotAs(OutputType.FILE).toPath(), screenshotFilename("define"));
    }
    @Test
    public void testEval() throws IOException {
        var driver = chrome.getWebDriver();

        driver.get("http://proxyNginx/search?query=3%2B3");
        System.out.println(driver.getTitle());
        System.out.println(driver.findElement(new By.ByXPath("//*")).getAttribute("outerHTML"));

        Files.move(driver.getScreenshotAs(OutputType.FILE).toPath(), screenshotFilename("eval"));
    }
    @Test
    public void testBang() throws IOException {
        var driver = chrome.getWebDriver();

        driver.get("http://proxyNginx/search?query=!g test");

        Files.move(driver.getScreenshotAs(OutputType.FILE).toPath(), screenshotFilename("bang"));
    }
}
