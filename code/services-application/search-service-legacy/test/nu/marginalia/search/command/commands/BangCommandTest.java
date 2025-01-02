package nu.marginalia.search.command.commands;

import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.exceptions.RedirectException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BangCommandTest {
    public BangCommand bangCommand = new BangCommand();

    @Test
    public void testG() {
        try {
            bangCommand.process(null,
                    new SearchParameters(" !g test",
                    null, null, null, null, null, false, 1)
            );
            Assertions.fail("Should have thrown RedirectException");
        }
        catch (RedirectException ex) {
            assertEquals("https://www.google.com/search?q=test", ex.newUrl);
        }
    }

    @Test
    public void testMatchPattern() {
        var match = bangCommand.matchBangPattern("!g test", "!g");

        assertTrue(match.isPresent());
        assertEquals(match.get(), "test");
    }

    @Test
    public void testMatchPattern2() {
        var match = bangCommand.matchBangPattern("test !g", "!g");

        assertTrue(match.isPresent());
        assertEquals(match.get(), "test");
    }

    @Test
    public void testMatchPattern3() {
        var match = bangCommand.matchBangPattern("hello !g world", "!g");

        assertTrue(match.isPresent());
        assertEquals(match.get(), "hello world");
    }

}