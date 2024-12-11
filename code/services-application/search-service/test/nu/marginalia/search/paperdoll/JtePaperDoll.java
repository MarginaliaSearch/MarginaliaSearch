package nu.marginalia.search.paperdoll;

import gg.jte.CodeResolver;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import gg.jte.resolve.DirectoryCodeResolver;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.search.model.NavbarModel;
import nu.marginalia.search.rendering.MockedSearchResults;
import nu.marginalia.service.server.StaticResources;
import org.junit.jupiter.api.Test;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.nio.file.Path;
import java.util.Map;

public class JtePaperDoll {
    final CodeResolver codeResolver = new DirectoryCodeResolver(Path.of(".").toAbsolutePath().resolve("resources/jte"));
    final TemplateEngine templateEngine = TemplateEngine.create(codeResolver, ContentType.Html);
    final StaticResources staticResources = new StaticResources();
    private String render(String template, Object obj) {
        var str = new StringOutput();
        templateEngine.render(template, obj, str);
        return str.toString();
    }

    private String render(String template, Map<String, Object> map) {
        var str = new StringOutput();
        templateEngine.render(template, map, str);
        return str.toString();
    }

    private Object serveStatic(Request request, Response response) {
        String resource = request.params("resource");
        staticResources.serveStatic("search", resource, request, response);
        return "";
    }

    @Test
    public void searchResults() {
        System.setProperty("test-env", "true");
        System.out.println(Path.of(".").toAbsolutePath());

        Spark.staticFileLocation("static/search");
        Spark.port(9999);

        Spark.after((rq, rs) -> {
            rs.header("Content-Encoding", "gzip");
        });
        Spark.get("/suggest/", (rq, rs) -> {
            rs.type("application/json");
            return "[\"pla\",\"plaa\",\"plab\",\"plac\",\"pla2b\",\"pla2l\",\"plaaz\",\"plac1\",\"placa\",\"place\"]";
        });
        Spark.get("/",
                (rq, rs) -> MockedSearchResults.mockRegularSearchResults(),
                ret -> this.render("serp/main.jte", Map.of("results", ret, "navbar", NavbarModel.SEARCH))
        );
        Spark.get("/site-focus",
                (rq, rs) -> MockedSearchResults.mockSiteFocusResults(),
                ret -> this.render("serp/main.jte", Map.of("results", ret, "navbar", NavbarModel.SEARCH))
        );
        Spark.get("/errors",
                (rq, rs) ->  MockedSearchResults.mockErrorData(),
                ret -> this.render("serp/error.jte", Map.of("model", ret, "navbar", NavbarModel.LIMBO))
        );
        Spark.get("/first",
                (rq, rs) ->  new Object(),
                ret -> this.render("serp/start.jte", Map.of( "navbar", NavbarModel.SEARCH,
                                                                             "websiteUrl", new WebsiteUrl("https://localhost:9999/")
                        ))
        );
        Spark.get("/explore",
                (rq, rs) ->  MockedSearchResults.mockBrowseResults(32),
                ret -> this.render("explore/main.jte", Map.of( "navbar", NavbarModel.EXPLORE,
                        "results", ret)
                )
        );
        Spark.get("/site-info",
                (rq, rs) ->  {
                    if ("links".equals(rq.queryParams("view"))) {
                        return MockedSearchResults.mockBacklinkData();
                    }
                    else if ("docs".equals(rq.queryParams("view"))) {
                        return MockedSearchResults.mockDocsData();
                    }
                    else if ("report".equals(rq.queryParams("view"))) {
                        return MockedSearchResults.mockReportDomain();
                    }
                    else return MockedSearchResults.mockSiteInfoData();

                },
                ret -> this.render("siteinfo/main.jte", Map.of("model", ret, "navbar", NavbarModel.SITEINFO))
        );
        Spark.get("/site-info-start",
                (rq, rs) -> MockedSearchResults.mockSiteInfoOverview(),
                ret -> this.render("siteinfo/start.jte", Map.of("model", ret, "navbar", NavbarModel.SITEINFO))
        );

        Spark.get("/site-info-crosstalk-ab",
                (rq, rs) -> MockedSearchResults.mockCrosstalkModel(),
                ret -> this.render("siteinfo/crosstalk.jte", Map.of("model", ret, "navbar", NavbarModel.SITEINFO))
        );
        Spark.get("/screenshot/*", (rq, rsp) -> {
            rsp.type("image/svg+xml");

            return """
                    <svg viewBox="0 0 800 600" xmlns="http://www.w3.org/2000/svg">
                      <!-- Browser Window Frame -->
                      <rect x="0" y="0" width="800" height="600" rx="8" fill="#f1f5f9"/>
                      <rect x="0" y="0" width="800" height="40" rx="8" fill="#e2e8f0"/>
                      <!-- Browser Controls -->
                      <circle cx="20" cy="20" r="6" fill="#ef4444"/>
                      <circle cx="40" cy="20" r="6" fill="#fbbf24"/>
                      <circle cx="60" cy="20" r="6" fill="#22c55e"/>
                      <!-- Address Bar -->
                      <rect x="120" y="10" width="560" height="20" rx="4" fill="#ffffff"/>
                      <!-- Content Area -->
                      <rect x="20" y="60" width="760" height="80" rx="4" fill="#ffffff"/>
                      <rect x="40" y="80" width="400" height="16" rx="2" fill="#cbd5e1"/>
                      <rect x="40" y="104" width="300" height="16" rx="2" fill="#cbd5e1"/>
                      <!-- Navigation -->
                      <rect x="20" y="160" width="180" height="420" rx="4" fill="#ffffff"/>
                      <rect x="40" y="180" width="140" height="12" rx="2" fill="#cbd5e1"/>
                      <rect x="40" y="200" width="120" height="12" rx="2" fill="#cbd5e1"/>
                      <rect x="40" y="220" width="130" height="12" rx="2" fill="#cbd5e1"/>
                      <!-- Main Content -->
                      <rect x="220" y="160" width="560" height="420" rx="4" fill="#ffffff"/>
                      <rect x="240" y="180" width="520" height="180" rx="2" fill="#cbd5e1"/>
                      <rect x="240" y="380" width="520" height="12" rx="2" fill="#cbd5e1"/>
                      <rect x="240" y="400" width="480" height="12" rx="2" fill="#cbd5e1"/>
                      <rect x="240" y="420" width="500" height="12" rx="2" fill="#cbd5e1"/>
                      <rect x="240" y="440" width="460" height="12" rx="2" fill="#cbd5e1"/>
                    </svg>
                    """;

        });


        Spark.init();

        for (;;);
    }

}
