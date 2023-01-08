package nu.marginalia.wmsa.edge.converting.loader;

import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.util.TestUtil;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadProcessedDocument;
import nu.marginalia.wmsa.edge.converting.processor.logic.HtmlFeature;
import nu.marginalia.wmsa.edge.dbcommon.EdgeDataStoreDaoImpl;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeHtmlStandard;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlState;
import nu.marginalia.wmsa.edge.model.id.EdgeIdArray;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
    EdgeDataStoreDaoImpl dataStoreDao;

    @BeforeEach
    public void setUp() throws URISyntaxException {
        dataSource = TestUtil.getConnection(mariaDBContainer.getJdbcUrl());
        dataStoreDao = new EdgeDataStoreDaoImpl(dataSource);

        var loadDomains = new SqlLoadDomains(dataSource);
        var loadUrls = new SqlLoadUrls(dataSource);

        loaderData = new LoaderData(10);

        loaderData.setTargetDomain(new EdgeDomain("www.marginalia.nu"));
        loadDomains.load(loaderData, new EdgeDomain("www.marginalia.nu"));

        loadUrls.load(loaderData, new EdgeUrl[]{new EdgeUrl("https://www.marginalia.nu/")});

    }

    @AfterEach
    public void tearDown() {
        dataStoreDao.clearCaches();
        dataSource.close();
    }

    @Test
    public void loadProcessedDocument() throws URISyntaxException {
        var loader = new SqlLoadProcessedDocument(dataSource);
        var url = new EdgeUrl("https://www.marginalia.nu/");

        loader.load(loaderData, List.of(new LoadProcessedDocument(
                url,
                EdgeUrlState.OK,
                "TITLE",
                "DESCR",
                HtmlFeature.encode(Set.of(HtmlFeature.AFFILIATE_LINK)),
                EdgeHtmlStandard.HTML5,
                100,
                12345,
                -3.14,
                null
        )));

        var details = dataStoreDao.getUrlDetailsMulti(new EdgeIdArray<>(loaderData.getUrlId(new EdgeUrl("https://www.marginalia.nu/"))));
        assertEquals(1, details.size());

        var urlDetails = details.get(0);

        assertEquals("TITLE", urlDetails.getTitle());
        assertEquals("DESCR", urlDetails.getDescription());
        assertTrue(urlDetails.isAffiliate());
        assertEquals(100, urlDetails.words);
        assertEquals(12345, urlDetails.dataHash);
        assertEquals(-3.14, urlDetails.getUrlQuality());
    }

}