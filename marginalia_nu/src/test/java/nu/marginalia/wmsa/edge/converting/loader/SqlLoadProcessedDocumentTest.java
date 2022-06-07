package nu.marginalia.wmsa.edge.converting.loader;

import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.util.TestUtil;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadProcessedDocument;
import nu.marginalia.wmsa.edge.converting.processor.logic.HtmlFeature;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeHtmlStandard;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

@ResourceLock(value = "mariadb", mode = ResourceAccessMode.READ_WRITE)
@Execution(ExecutionMode.SAME_THREAD)
@Tag("db")
class SqlLoadProcessedDocumentTest {
    HikariDataSource dataSource;
    LoaderData loaderData;
    @BeforeEach
    public void setUp() throws URISyntaxException {
        dataSource = TestUtil.getConnection();
        TestUtil.evalScript(dataSource, "sql/edge-crawler-cache.sql");

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
        loader.load(loaderData, List.of(new LoadProcessedDocument(
                new EdgeUrl("https://www.marginalia.nu/"),
                EdgeUrlState.OK,
                "TITLE",
                "DESCR",
                HtmlFeature.encode(Set.of(HtmlFeature.AFFILIATE_LINK)),
                EdgeHtmlStandard.HTML5,
                100,
                12345,
                -5
        )));
    }

}