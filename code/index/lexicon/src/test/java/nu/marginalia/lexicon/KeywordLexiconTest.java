package nu.marginalia.lexicon;

import nu.marginalia.dict.OnHeapDictionaryMap;
import nu.marginalia.lexicon.journal.KeywordLexiconJournal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class KeywordLexiconTest {

    private Path journalFile;
    private KeywordLexicon lexicon;

    @BeforeEach
    public void setUp() throws IOException {
        journalFile = Files.createTempFile(getClass().getSimpleName(), ".dat");

        var lexiconJournal = new KeywordLexiconJournal(journalFile.toFile());
        lexicon = new KeywordLexicon(lexiconJournal);
    }

    @AfterEach
    public void tearDown() throws Exception {
        lexicon.close();
        Files.delete(journalFile);
    }

    @Test
    public void testConsistentInserts() {
        int a = lexicon.getOrInsert("aaa");
        int b = lexicon.getOrInsert("bbb");
        int a2 = lexicon.getOrInsert("aaa");
        int c = lexicon.getOrInsert("ccc");

        assertEquals(a, a2);
        assertNotEquals(a, b);
        assertNotEquals(a, c);
        assertNotEquals(b, c);
    }

    @Test
    public void testInsertReplay() {
        int a = lexicon.getOrInsert("aaa");
        int b = lexicon.getOrInsert("bbb");
        int c = lexicon.getOrInsert("ccc");

        assertEquals(a, lexicon.getReadOnly("aaa"));
        assertEquals(b, lexicon.getReadOnly("bbb"));
        assertEquals(c, lexicon.getReadOnly("ccc"));
    }

    @Test
    public void testReload() throws IOException {
        int a = lexicon.getOrInsert("aaa");
        int b = lexicon.getOrInsert("bbb");
        int c = lexicon.getOrInsert("ccc");
        lexicon.commitToDisk();

        var lexiconJournal = new KeywordLexiconJournal(journalFile.toFile());
        try (var anotherLexicon = new KeywordLexicon(lexiconJournal)) {
            assertEquals(a, anotherLexicon.getReadOnly("aaa"));
            assertEquals(b, anotherLexicon.getReadOnly("bbb"));
            assertEquals(c, anotherLexicon.getReadOnly("ccc"));
        }
        catch (Exception ex) {
            Assertions.fail("???", ex);
        }
    }
}
