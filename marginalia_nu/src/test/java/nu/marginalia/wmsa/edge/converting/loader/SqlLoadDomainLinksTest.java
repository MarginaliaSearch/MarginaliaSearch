package nu.marginalia.wmsa.edge.converting.loader;

import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.util.TestUtil;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DomainLink;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SqlLoadDomainLinksTest {
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
        dataSource = TestUtil.getConnection(mariaDBContainer.getJdbcUrl());

        var loadDomains = new SqlLoadDomains(dataSource);
        loaderData = new LoaderData(10);

        loaderData.setTargetDomain(new EdgeDomain("www.marginalia.nu"));
        loadDomains.load(loaderData, new EdgeDomain[] { new EdgeDomain("www.marginalia.nu"), new EdgeDomain("memex.marginalia.nu") });
    }

    @AfterEach
    public void tearDown() {
        dataSource.close();
    }

    @Test
    public void loadDomainLinks() {
        var loader = new SqlLoadDomainLinks(dataSource);
        loader.load(new DomainLink[] { new DomainLink(new EdgeDomain("www.marginalia.nu"), new EdgeDomain("memex.marginalia.nu")) });
    }

}