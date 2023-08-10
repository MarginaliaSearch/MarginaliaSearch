package nu.marginalia.loader;

import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.loading.loader.LoaderData;
import nu.marginalia.loading.loader.SqlLoadDomainLinks;
import nu.marginalia.loading.loader.SqlLoadDomains;
import nu.marginalia.converting.instruction.instructions.DomainLink;
import nu.marginalia.model.EdgeDomain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("slow")
@Testcontainers
class SqlLoadDomainLinksTest {
    @Container
    static MariaDBContainer<?> mariaDBContainer = new MariaDBContainer<>("mariadb")
                .withDatabaseName("WMSA_prod")
                .withUsername("wmsa")
                .withPassword("wmsa")
                .withInitScript("db/migration/V23_06_0_000__base.sql")
                .withNetworkAliases("mariadb");

    HikariDataSource dataSource;
    LoaderData loaderData;
    @BeforeEach
    public void setUp() {
        dataSource = DbTestUtil.getConnection(mariaDBContainer.getJdbcUrl());

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
        loader.load(loaderData, new DomainLink[] { new DomainLink(new EdgeDomain("www.marginalia.nu"), new EdgeDomain("memex.marginalia.nu")) });
    }

}