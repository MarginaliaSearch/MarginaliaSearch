package nu.marginalia.search.command;

import io.jooby.ModelAndView;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.search.model.SearchParameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class LangCommandTest {
    LangCommand langCommand = new LangCommand();

    @Test
    void testProcess() {
        assertRedirect(langCommand.process(SearchParameters.defaultsForQuery(
                new WebsiteUrl("https://www.example.com/"), "cats lang:sv", 1
        )));
        assertRedirect(langCommand.process(SearchParameters.defaultsForQuery(
                new WebsiteUrl("https://www.example.com/"), "cats lang:sv dogs", 1
        )));
        assertRedirect(langCommand.process(SearchParameters.defaultsForQuery(
                new WebsiteUrl("https://www.example.com/"), "lang:sv", 1
        )));
        assertNoRedirect(langCommand.process(SearchParameters.defaultsForQuery(
                new WebsiteUrl("https://www.example.com/"), "lang:svenska", 1
        )));
        assertNoRedirect(langCommand.process(SearchParameters.defaultsForQuery(
                new WebsiteUrl("https://www.example.com/"), "slang:sv", 1
        )));
    }

    static void assertRedirect(Optional<ModelAndView<?>> result) {
        Assertions.assertTrue(result.isPresent());
        System.out.println("Redirect: " + result.get().getModel());
    }
    static void assertNoRedirect(Optional<ModelAndView<?>> result) {
        Assertions.assertTrue(result.isEmpty());
    }

}