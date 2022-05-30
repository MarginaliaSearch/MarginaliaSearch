package nu.marginalia.wmsa.edge.search.command.commands;

import nu.marginalia.wmsa.edge.search.exceptions.RedirectException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

class BangCommandTest {

    @Test
    public void testBang() {
        var bc = new BangCommand();

        expectRedirectUrl("https://www.google.com/search?q=search+terms", () -> bc.process(null, null, "search terms !g"));
        expectNoRedirect(() -> bc.process(null, null, "search terms!g"));
        expectNoRedirect(() -> bc.process(null, null, "!gsearch terms"));
        expectRedirectUrl("https://www.google.com/search?q=search+terms", () -> bc.process(null, null, "!g search terms"));
    }

    void expectNoRedirect(Runnable op) {
        try {
            op.run();
        }
        catch (RedirectException ex) {
            fail("Expected no redirection, but got " + ex.newUrl);
        }
    }
    void expectRedirectUrl(String expectedUrl, Runnable op) {
        try {
            op.run();
            fail("Didn't intercept exception");
        }
        catch (RedirectException ex) {
            Assertions.assertEquals(expectedUrl, ex.newUrl, "Unexpected redirect");
        }
    }
}