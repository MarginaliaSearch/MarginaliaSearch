package nu.marginalia.wmsa.edge.converting.loader;

import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.util.TestUtil;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(value = "mariadb", mode = ResourceAccessMode.READ_WRITE)
@Execution(ExecutionMode.SAME_THREAD)
@Tag("db")
class SqlLoadUrlsTest {
    HikariDataSource dataSource;
    LoaderData loaderData;
    @BeforeEach
    public void setUp() {
        dataSource = TestUtil.getConnection();
        TestUtil.evalScript(dataSource, "sql/edge-crawler-cache.sql");

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