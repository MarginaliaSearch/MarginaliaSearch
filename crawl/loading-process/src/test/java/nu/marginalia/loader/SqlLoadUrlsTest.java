package nu.marginalia.loader;

import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.loading.loader.LoaderData;
import nu.marginalia.loading.loader.SqlLoadDomains;
import nu.marginalia.loading.loader.SqlLoadUrls;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URISyntaxException;

@Tag("slow")
@Testcontainers
class SqlLoadUrlsTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withInitScript("sql/edge-crawler-cache.sql")
            .withNetworkAliases("mariadb");

    HikariDataSource dataSource;
    LoaderData loaderData;
    @BeforeEach
    public void setUp() {
        dataSource = DbTestUtil.getConnection(mariaDBContainer.getJdbcUrl());

        var loadDomains = new SqlLoadDomains(dataSource);
        loaderData = new LoaderData(10);

        loaderData.setTargetDomain(new EdgeDomain("www.marginalia.nu"));
        loadDomains.load(loaderData, new EdgeDomain("www.marginalia.nu"));
    }

    @AfterEach
    public void tearDown() {
        dataSource.close();
    }

    @Test
    public void loadUrl() throws URISyntaxException {
        var loadUrls = new SqlLoadUrls(dataSource);
        loadUrls.load(loaderData, new EdgeUrl[] { new EdgeUrl("https://www.marginalia.nu/") });
    }

}