package nu.marginalia.gmi;

import nu.marginalia.memex.gemini.gmi.GemtextDatabase;
import nu.marginalia.memex.gemini.gmi.GemtextDocument;
import nu.marginalia.memex.gemini.gmi.line.GemtextLink;
import nu.marginalia.memex.memex.model.MemexNodeUrl;
import nu.marginalia.memex.memex.model.MemexUrl;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class GemtextDocumentTest {

    @Test
    void testEmpty() {
        var document = GemtextDocument.of(new MemexNodeUrl("/test.gmi"), "");
        assertEquals("/test.gmi", document.getTitle());
        assertTrue(document.getLinks().isEmpty());
    }

    @Test
    void testParseTombstone() {
        var lines = new String[] {
               "# Tombstone",
"",
        "This special file contains information about removed resources.",
"",
"# Removed links",
"=> /dead.gmi	It was never here, I swear",
                "=> /dead2.png	Removed through an act of God",
                "=> /worklog.gmi	Old and unused file.",
                "=> /todo.gmi	Empty file",
                "=> /search-about.gmi	Confusingly gemini-specific",
                "=> /05-test.gmi	Cursed testing file"};

        var document = GemtextDatabase.of(new MemexNodeUrl("/test.gmi"), lines);
        Arrays.stream(document.getLines()).forEach(System.out::println);
        document.keys().forEach(k -> System.out.println(k + "-" + document.getLinkData(new MemexNodeUrl(k)).orElse("")));

}
    @Test
    void testVanilla() {
        var document = GemtextDocument.of(new MemexNodeUrl("/test.gmi"),
                "# Test Document",
                "=> /foo.gmi\tMy foos",
                "=>/bar.gmi\tMy bars",
                "=>/baz.gmi",
                "=>/foobar.gmi ",
                "=>/volvo240.png hey cool car right",
                "=>",
                "=> ",
                " => ",
                "## Goodbye",
                "... and good luck");
        assertEquals("Test Document", document.getTitle());
        Arrays.stream(document.getLines()).forEach(System.out::println);
        assertEquals(5, document.getLinks().size());
        document.getLinks().forEach(System.out::println);

        assertArrayEquals(new String[] {
                "/foo.gmi", "/bar.gmi", "/baz.gmi", "/foobar.gmi", "/volvo240.png"
                },
                document.getLinks().stream().map(GemtextLink::getUrl).map(MemexUrl::getUrl).toArray());

        assertArrayEquals(new String[] {"My foos", "My bars", null, null, "hey cool car right"},
                document.getLinks().stream().map(GemtextLink::getTitle).toArray());
    }



    @Test
    void testTasks() {
        var document = GemtextDocument.of(new MemexNodeUrl("/test.gmi"),
                "# Test Document",
                "- Go shopping",
                "-- Milk",
                "-- Eggs",
                "-- Bacon",
                "--- If they have organic, buy two",
                "- Go dancing",
                "Stuff",
                "- Go dancing again");
        assertEquals("Test Document", document.getTitle());
        Arrays.stream(document.getLines()).forEach(System.out::println);
        document.getOpenTopTasks().values().forEach(System.out::println);
    }

}