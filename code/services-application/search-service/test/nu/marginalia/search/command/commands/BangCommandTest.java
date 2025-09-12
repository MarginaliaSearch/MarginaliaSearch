package nu.marginalia.search.command.commands;

import nu.marginalia.WebsiteUrl;
import nu.marginalia.search.model.SearchParameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BangCommandTest {

    @Test
    void testWikipediaRedirect() {
        BangCommand bc = new BangCommand();

        assertTrue(bc.process(SearchParameters.defaultsForQuery(new WebsiteUrl("test"), "!w plato", 1)).isPresent());
        assertFalse(bc.process(SearchParameters.defaultsForQuery(new WebsiteUrl("test"), "plato", 1)).isPresent());
    }
}