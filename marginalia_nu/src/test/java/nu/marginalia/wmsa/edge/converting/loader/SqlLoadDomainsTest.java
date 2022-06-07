package nu.marginalia.wmsa.edge.converting.loader;

import nu.marginalia.util.TestUtil;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock(value = "mariadb", mode = ResourceAccessMode.READ_WRITE)
@Execution(ExecutionMode.SAME_THREAD)
@Tag("db")
class SqlLoadDomainsTest {


    @Test
    public void loadDomain() {

        try (var dataSource = TestUtil.getConnection()) {
            TestUtil.evalScript(dataSource, "sql/edge-crawler-cache.sql");

            var loadDomains = new SqlLoadDomains(dataSource);
            var loaderData = new LoaderData(10);

            loaderData.setTargetDomain(new EdgeDomain("www.marginalia.nu"));
            loadDomains.load(loaderData, new EdgeDomain("www.marginalia.nu"));

            assertTrue(loaderData.getDomainId(new EdgeDomain("www.marginalia.nu")) >= 0);
        }

    }

    @Test
    public void loadDomains() {

        try (var dataSource = TestUtil.getConnection()) {
            TestUtil.evalScript(dataSource, "sql/edge-crawler-cache.sql");

            var loadDomains = new SqlLoadDomains(dataSource);
            var loaderData = new LoaderData(10);

            loaderData.setTargetDomain(new EdgeDomain("www.marginalia.nu"));
            loadDomains.load(loaderData, new EdgeDomain[] { new EdgeDomain("www.marginalia.nu"), new EdgeDomain("memex.marginalia.nu") });

            assertTrue(loaderData.getDomainId(new EdgeDomain("www.marginalia.nu")) >= 0);
            assertTrue(loaderData.getDomainId(new EdgeDomain("memex.marginalia.nu")) >= 0);
        }

    }
}