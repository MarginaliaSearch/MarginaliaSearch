package nu.marginalia.search.rendering;

import gg.jte.CodeResolver;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import gg.jte.resolve.DirectoryCodeResolver;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.language.config.LanguageConfigLocation;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.search.model.NavbarModel;
import nu.marginalia.search.svc.SearchFrontPageService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** This test class verifies that the templates render successfully.
 * It does not perform checks that the output is correct */
public class RenderingTest {
    private static LanguageConfiguration languageConfiguration;
    final CodeResolver codeResolver = new DirectoryCodeResolver(Path.of(".").toAbsolutePath().resolve("resources/jte"));
    final TemplateEngine templateEngine = TemplateEngine.create(codeResolver, ContentType.Html);

    @BeforeAll
    public static void setUpAll() throws IOException, ParserConfigurationException, SAXException {
        languageConfiguration = new LanguageConfiguration(new LanguageConfigLocation.Experimental());
    }

    @Test
    public void testSerp_Main() throws URISyntaxException {
        templateEngine.render("serp/main.jte",
                Map.of("results", MockedSearchResults.mockRegularSearchResults(),
                        "navbar", NavbarModel.SEARCH,
                        "languageDefinitions", languageConfiguration.languagesMap()),
                new StringOutput());
    }

    @Test
    public void testSerp_SiteFocus() throws URISyntaxException {
        templateEngine.render("serp/main.jte",
                Map.of("results", MockedSearchResults.mockSiteFocusResults(),
                        "navbar", NavbarModel.SEARCH,
                        "languageDefinitions", languageConfiguration.languagesMap()),
                new StringOutput());
    }

    @Test
    public void testSerp_Error() {
        templateEngine.render("serp/error.jte",
                Map.of("model", MockedSearchResults.mockErrorData(),
                        "navbar", NavbarModel.SEARCH),
                new StringOutput());
    }

    @Test
    public void testSerp_First() {
        templateEngine.render("serp/start.jte",
                Map.of( "navbar", NavbarModel.SEARCH,
                        "model", new SearchFrontPageService.IndexModel(List.of(), "", 4),
                        "websiteUrl", new WebsiteUrl("https://localhost:9999/")
                ),
                new StringOutput());
    }

    @Test
    public void testExplore() {
        templateEngine.render("explore/main.jte",
                Map.of( "navbar", NavbarModel.EXPLORE,
                        "results", MockedSearchResults.mockBrowseResults(32)),
                new StringOutput());
    }

    @Test
    public void testSiteInfo_Links() throws URISyntaxException {
        templateEngine.render("siteinfo/main.jte",
                Map.of( "navbar", NavbarModel.SITEINFO,
                        "model", MockedSearchResults.mockBacklinkData()
                ),
                new StringOutput());
    }

    @Test
    public void testSiteInfo_Docs() throws URISyntaxException {
        templateEngine.render("siteinfo/main.jte",
                Map.of( "navbar", NavbarModel.SITEINFO,
                        "model", MockedSearchResults.mockDocsData()
                ),
                new StringOutput());
    }

    @Test
    public void testSiteInfo_Report() {
        templateEngine.render("siteinfo/main.jte",
                Map.of( "navbar", NavbarModel.SITEINFO,
                        "model", MockedSearchResults.mockReportDomain()
                ),
                new StringOutput());
    }

    @Test
    public void testSiteInfo_Overview() throws URISyntaxException {
        templateEngine.render("siteinfo/main.jte",
                Map.of( "navbar", NavbarModel.SITEINFO,
                        "model", MockedSearchResults.mockSiteInfoData()
                ),
                new StringOutput());
    }

    @Test
    public void testSiteInfo_Crosstalk() throws URISyntaxException {
        templateEngine.render("siteinfo/crosstalk.jte",
                Map.of( "navbar", NavbarModel.SITEINFO,
                        "model", MockedSearchResults.mockCrosstalkModel()
                ),
                new StringOutput());
    }

    @Test
    public void testSiteInfo_Start() {
        templateEngine.render("siteinfo/start.jte",
                Map.of( "navbar", NavbarModel.SITEINFO,
                        "model", MockedSearchResults.mockSiteInfoOverview()
                ),
                new StringOutput());
    }

}
