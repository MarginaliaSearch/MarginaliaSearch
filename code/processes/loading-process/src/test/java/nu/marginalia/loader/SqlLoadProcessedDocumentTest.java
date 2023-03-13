package nu.marginalia.loader;

import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocument;
import nu.marginalia.search.db.DbUrlDetailsQuery;
import nu.marginalia.loading.loader.LoaderData;
import nu.marginalia.loading.loader.SqlLoadDomains;
import nu.marginalia.loading.loader.SqlLoadProcessedDocument;
import nu.marginalia.loading.loader.SqlLoadUrls;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.converting.model.HtmlStandard;
import nu.marginalia.model.crawl.UrlIndexingState;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.id.EdgeIdArray;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("slow")
@Testcontainers
class SqlLoadProcessedDocumentTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withInitScript("sql/edge-crawler-cache.sql")
            .withNetworkAliases("mariadb");

    HikariDataSource dataSource;
    LoaderData loaderData;

    DbUrlDetailsQuery dbUrlDetailsQuery;
    @BeforeEach
    public void setUp() throws URISyntaxException {
        dataSource = DbTestUtil.getConnection(mariaDBContainer.getJdbcUrl());
        dbUrlDetailsQuery = new DbUrlDetailsQuery(dataSource);

        var loadDomains = new SqlLoadDomains(dataSource);
        var loadUrls = new SqlLoadUrls(dataSource);

        loaderData = new LoaderData(10);

        loaderData.setTargetDomain(new EdgeDomain("www.marginalia.nu"));
        loadDomains.load(loaderData, new EdgeDomain("www.marginalia.nu"));

        loadUrls.load(loaderData, new EdgeUrl[]{new EdgeUrl("https://www.marginalia.nu/")});

    }

    @AfterEach
    public void tearDown() {
        dataSource.close();
    }

    @Test
    public void loadProcessedDocument() throws URISyntaxException {
        var loader = new SqlLoadProcessedDocument(dataSource);
        var url = new EdgeUrl("https://www.marginalia.nu/");

        loader.load(loaderData, List.of(new LoadProcessedDocument(
                url,
                UrlIndexingState.OK,
                "TITLE",
                "DESCR",
                HtmlFeature.encode(Set.of(HtmlFeature.AFFILIATE_LINK)),
                HtmlStandard.HTML5.name(),
                100,
                12345,
                -3.14,
                null
        )));

        var details = dbUrlDetailsQuery.getUrlDetailsMulti(new EdgeIdArray<>(loaderData.getUrlId(new EdgeUrl("https://www.marginalia.nu/"))));
        Assertions.assertEquals(1, details.size());

        var urlDetails = details.get(0);

        assertEquals("TITLE", urlDetails.getTitle());
        assertEquals("DESCR", urlDetails.getDescription());
        assertTrue(urlDetails.isAffiliate());
        assertEquals(100, urlDetails.words);
        assertEquals(12345, urlDetails.dataHash);
        assertEquals(-3.14, urlDetails.getUrlQuality());
    }

}