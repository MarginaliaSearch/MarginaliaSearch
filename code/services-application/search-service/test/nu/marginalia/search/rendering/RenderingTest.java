package nu.marginalia.search.rendering;

import gg.jte.CodeResolver;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import gg.jte.resolve.DirectoryCodeResolver;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;

/** This test class verifies that the templates render successfully.
 * It does not perform checks that the output is correct */
public class RenderingTest {
    final CodeResolver codeResolver = new DirectoryCodeResolver(Path.of(".").toAbsolutePath().resolve("resources/jte"));
    final TemplateEngine templateEngine = TemplateEngine.create(codeResolver, ContentType.Html);



    @Test
    public void testSerp_Main() throws URISyntaxException {
        templateEngine.render("serp/main.jte", MockedSearchResults.mockRegularSearchResults(), new StringOutput());
    }

    @Test
    public void testSerp_SiteFocus() throws URISyntaxException {
        templateEngine.render("serp/main.jte", MockedSearchResults.mockSiteFocusResults(), new StringOutput());
    }
}
