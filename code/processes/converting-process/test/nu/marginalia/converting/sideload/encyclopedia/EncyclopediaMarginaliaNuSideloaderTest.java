package nu.marginalia.converting.sideload.encyclopedia;

import com.google.inject.Guice;
import nu.marginalia.atags.AnchorTextKeywords;
import nu.marginalia.atags.source.AnchorTagsSourceFactory;
import nu.marginalia.converting.ConverterModule;
import nu.marginalia.converting.sideload.SideloaderProcessing;
import nu.marginalia.encyclopedia.EncyclopediaConverter;
import nu.marginalia.encyclopedia.cleaner.model.ArticleParts;
import nu.marginalia.encyclopedia.model.Article;
import nu.marginalia.encyclopedia.model.LinkList;
import nu.marginalia.encyclopedia.store.ArticleDbProvider;
import nu.marginalia.encyclopedia.store.ArticleStoreWriter;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.process.ProcessConfigurationModule;
import nu.marginalia.service.module.DatabaseModule;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Tag("slow")
@Testcontainers
class EncyclopediaMarginaliaNuSideloaderTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withNetworkAliases("mariadb");

    static AnchorTagsSourceFactory anchorTagsSourceFactory;
    static AnchorTextKeywords anchorTextKeywords;
    static SideloaderProcessing sideloaderProcessing;

    @BeforeAll
    public static void setUpAll() throws IOException {
        System.setProperty("db.overrideJdbc", mariaDBContainer.getJdbcUrl());
        System.setProperty("system.serviceNode", "1");

        var injector = Guice.createInjector(
                new ConverterModule(),
                new DatabaseModule(true),
                new ProcessConfigurationModule("test"));

        anchorTagsSourceFactory = injector.getInstance(AnchorTagsSourceFactory.class);
        anchorTextKeywords = injector.getInstance(AnchorTextKeywords.class);
        sideloaderProcessing = injector.getInstance(SideloaderProcessing.class);
    }

    @AfterAll
    public static void tearDown() throws IOException {
    }


    @Test
    public void testFullConvert() throws IOException {
        Path inputFile = Path.of("/home/vlofgren/Work/wikipedia_en_100_2025-07.zim");
        if (!Files.exists(inputFile))
            return;

        Path outputFile = Files.createTempFile("encyclopedia", ".db");
        try {
            EncyclopediaConverter.convert(inputFile, outputFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            Files.delete(outputFile);
        }
    }

    @Test
    public void testSunnyDay() throws Exception {

        Path fileName = Files.createTempFile(getClass().getSimpleName(), ".db");
        try {
            ArticleDbProvider dbProvider = new ArticleDbProvider(fileName);

            try (ArticleStoreWriter writer = new ArticleStoreWriter(dbProvider)) {

                writer.add(new Article(
                        "shoes",
                        "Shoes",
                        "Lorem ipsum dolor sit amet",
                        new ArticleParts("""
                                A shoe is an item of footwear intended to protect and comfort the human foot. Though the human foot can adapt to varied terrains and climate conditions, it is vulnerable, and shoes provide protection. Form was originally tied to function, but over time, shoes also became fashion items. Some shoes are worn as safety equipment, such as steel-toe boots, which are required footwear at industrial worksites.
                                """),
                        new LinkList(),
                        new LinkList()
                ).asData());
            }

            var sideloader = new EncyclopediaMarginaliaNuSideloader(fileName, "https://en.wikipedia.org/wiki/", GsonFactory.get(), anchorTagsSourceFactory, anchorTextKeywords, sideloaderProcessing);
            var domain = sideloader.getDomain();

            Assertions.assertEquals(new EdgeDomain("en.wikipedia.org"), domain.domain);

            var documentsStream = sideloader.getDocumentsStream();
            Assertions.assertTrue(documentsStream.hasNext());
            var doc = documentsStream.next();
            Assertions.assertEquals(new EdgeUrl("https://en.wikipedia.org/wiki/shoes"), doc.url);
            Assertions.assertFalse(documentsStream.hasNext());
        }
        finally {
            Files.deleteIfExists(fileName);
        }
    }

    @Test
    public void testDashRewriting() throws Exception {

        Path fileName = Files.createTempFile(getClass().getSimpleName(), ".db");
        try {
            ArticleDbProvider dbProvider = new ArticleDbProvider(fileName);

            try (ArticleStoreWriter writer = new ArticleStoreWriter(dbProvider)) {

                writer.add(new Article(
                        "tf\u2013idf",
                        "TF-IDF",
                        "Lorem ipsum dolor sit amet",
                        new ArticleParts(""),
                        new LinkList(),
                        new LinkList()
                ).asData());
            }

            var sideloader = new EncyclopediaMarginaliaNuSideloader(fileName, "https://en.wikipedia.org/wiki/", GsonFactory.get(), anchorTagsSourceFactory, anchorTextKeywords, sideloaderProcessing);
            var domain = sideloader.getDomain();

            Assertions.assertEquals(new EdgeDomain("en.wikipedia.org"), domain.domain);

            var documentsStream = sideloader.getDocumentsStream();
            Assertions.assertTrue(documentsStream.hasNext());
            var doc = documentsStream.next();
            Assertions.assertEquals(new EdgeUrl("https://en.wikipedia.org/wiki/tf-idf"), doc.url);
            Assertions.assertFalse(documentsStream.hasNext());
        }
        finally {
            Files.deleteIfExists(fileName);
        }
    }

    @Test
    public void testUrlencoding() throws Exception {

        Path fileName = Files.createTempFile(getClass().getSimpleName(), ".db");
        try {
            ArticleDbProvider dbProvider = new ArticleDbProvider(fileName);

            try (ArticleStoreWriter writer = new ArticleStoreWriter(dbProvider)) {

                writer.add(new Article(
                        "any percent",
                        "Any %",
                        "Summoning salt go brr",
                        new ArticleParts(""),
                        new LinkList(),
                        new LinkList()
                ).asData());
            }

            var sideloader = new EncyclopediaMarginaliaNuSideloader(fileName, "https://en.wikipedia.org/wiki/", GsonFactory.get(), anchorTagsSourceFactory, anchorTextKeywords, sideloaderProcessing);
            var domain = sideloader.getDomain();

            Assertions.assertEquals(new EdgeDomain("en.wikipedia.org"), domain.domain);

            var documentsStream = sideloader.getDocumentsStream();
            Assertions.assertTrue(documentsStream.hasNext());
            var doc = documentsStream.next();
            Assertions.assertEquals(new EdgeUrl("https://en.wikipedia.org/wiki/any+percent"), doc.url);
            Assertions.assertFalse(documentsStream.hasNext());
        }
        finally {
            Files.deleteIfExists(fileName);
        }
    }
}