package nu.marginalia.loader;

import nu.marginalia.loading.loader.LoaderData;
import nu.marginalia.loading.loader.SqlLoadDomains;
import nu.marginalia.model.EdgeDomain;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("slow")
@Testcontainers
class SqlLoadDomainsTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
            .withDatabaseName("WMSA_prod")
            .withUsername("wmsa")
            .withPassword("wmsa")
            .withInitScript("sql/current/00-base.sql")
            .withNetworkAliases("mariadb");

    @Test
    public void loadDomain() {

        try (var dataSource = DbTestUtil.getConnection(mariaDBContainer.getJdbcUrl())) {
            var loadDomains = new SqlLoadDomains(dataSource);
            var loaderData = new LoaderData(10);

            loaderData.setTargetDomain(new EdgeDomain("www.marginalia.nu"));
            loadDomains.load(loaderData, new EdgeDomain("www.marginalia.nu"));

            assertTrue(loaderData.getDomainId(new EdgeDomain("www.marginalia.nu")) >= 0);
        }

    }

    @Test
    public void loadDomains() {

        try (var dataSource = DbTestUtil.getConnection(mariaDBContainer.getJdbcUrl());) {
            var loadDomains = new SqlLoadDomains(dataSource);
            var loaderData = new LoaderData(10);

            loaderData.setTargetDomain(new EdgeDomain("www.marginalia.nu"));
            loadDomains.load(loaderData, new EdgeDomain[] { new EdgeDomain("www.marginalia.nu"), new EdgeDomain("memex.marginalia.nu") });

            assertTrue(loaderData.getDomainId(new EdgeDomain("www.marginalia.nu")) >= 0);
            assertTrue(loaderData.getDomainId(new EdgeDomain("memex.marginalia.nu")) >= 0);
        }

    }
}