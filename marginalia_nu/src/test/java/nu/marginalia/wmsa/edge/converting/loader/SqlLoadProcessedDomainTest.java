package nu.marginalia.wmsa.edge.converting.loader;

import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.util.TestUtil;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DomainLink;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.net.URISyntaxException;

@ResourceLock(value = "mariadb", mode = ResourceAccessMode.READ_WRITE)
@Execution(ExecutionMode.SAME_THREAD)
@Tag("db")
class SqlLoadProcessedDomainTest {
    HikariDataSource dataSource;
    LoaderData loaderData;
    @BeforeEach
    public void setUp() {
        dataSource = TestUtil.getConnection();
        TestUtil.evalScript(dataSource, "sql/edge-crawler-cache.sql");

        var loadDomains = new SqlLoadDomains(dataSource);
        loaderData = new LoaderData(10);

        loaderData.setTargetDomain(new EdgeDomain("www.marginalia.nu"));
        loadDomains.load(loaderData, new EdgeDomain[]{ new EdgeDomain("www.marginalia.nu"), new EdgeDomain("memex.marginalia.nu") });
    }

    @AfterEach
    public void tearDown() {
        dataSource.close();
    }

    @Test
    public void loadProcessedDomain() {
        var loader = new SqlLoadProcessedDomain(dataSource, new SqlLoadDomains(dataSource));
        loader.load(loaderData, new EdgeDomain("www.marginalia.nu"), EdgeDomainIndexingState.BLOCKED, "127.0.0.1");
    }
    @Test
    public void loadDomainAlias() {
        var loader = new SqlLoadProcessedDomain(dataSource, new SqlLoadDomains(dataSource));
        loader.loadAlias(loaderData, new DomainLink(new EdgeDomain("memex.marginalia.nu"), new EdgeDomain("www.marginalia.nu")));
    }
}