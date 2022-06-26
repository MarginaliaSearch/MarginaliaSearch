package nu.marginalia.wmsa.edge.index.service;

import lombok.SneakyThrows;
import nu.marginalia.util.dict.DictionaryHashMap;
import nu.marginalia.wmsa.edge.index.conversion.SearchIndexConverter;
import nu.marginalia.wmsa.edge.index.conversion.SearchIndexPartitioner;
import nu.marginalia.wmsa.edge.index.lexicon.KeywordLexicon;
import nu.marginalia.wmsa.edge.index.lexicon.KeywordLexiconReadOnlyView;
import nu.marginalia.wmsa.edge.index.lexicon.journal.KeywordLexiconJournal;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DictionaryWriterTest {
    /*
        @Test @Disabled

        public void analyze2() throws IOException {
            System.out.println("Loading dictionary");
            var dr = new DictionaryReader(null, new File("/home/vlofgren/dictionary.dat"));
            System.out.println("Loading indices");
            var reader = new SearchIndexReader(new SearchIndex("test", Path.of("/tmp"),
                                                                new File("/tmp/urls-0"),
                                                                new File("/tmp/words-0")),
                    new SearchIndex("test", Path.of("/tmp"),
                                                                new File("/tmp/urls-24"),
                                                                new File("/tmp/words-24")));
            System.out.println("Gogo");
            long hitsTotal = 0L;
            try (var wr = new PrintWriter(new FileOutputStream("/home/vlofgren/words-count"))) {
                hitsTotal = dr.stream().mapToLong(w -> {
                    long hits = reader.numHits(dr.get(w));
                    wr.printf("%08d %s\n", hits, w);
                    return hits;
                }).sum();
            }
            System.out.println(hitsTotal);
        }
    */
    @Test  @Disabled @SneakyThrows
    public void convert() {
        new SearchIndexConverter(IndexBlock.Title, 0, Path.of("/tmp"),
                new File("/home/vlofgren/page-index-0.dat"),
                new File("/tmp/words-0"),
                new File("/tmp/urls-0"),
                new SearchIndexPartitioner(null),
                val -> false);
    }
    
    KeywordLexiconJournal createJournal(File f) throws IOException {
        return new KeywordLexiconJournal(f);
    }

    @SneakyThrows
    @Test
    @Disabled
    void test() {
        try (var dict = new KeywordLexicon(createJournal(Path.of("/home/vlofgren/Code/data/dictionary.dat").toFile()), new DictionaryHashMap(1L<<16))) {
            wait();
        }
    }


    @SneakyThrows
    @Test
    void getFold() {
        var path = Files.createTempFile("dict", ".tmp");
        try (var dict = new KeywordLexicon(createJournal(path.toFile()), new DictionaryHashMap(1L<<16))) {
            dict.getOrInsert("hic");
            dict.getOrInsert("hac");
            dict.commitToDisk();
            dict.getOrInsert("quae");
            dict.getOrInsert("quis");
            dict.getOrInsert("quem1");
            dict.getOrInsert("quem2");
            dict.getOrInsert("quem3");
            dict.getOrInsert("quem4");
            dict.getOrInsert("quem5");
            dict.getOrInsert("quem6");
            dict.getOrInsert("quem7");
            dict.getOrInsert("quem8");
            dict.getOrInsert("quem9");
            dict.getOrInsert("quem10");
            dict.getOrInsert("cuis");
            dict.getOrInsert("haec_hic");
            dict.getOrInsert("hoc_hac_cuis");
            dict.commitToDisk();
            assertNotEquals(0, dict.getOrInsert("hac"));
            assertEquals(0, dict.getOrInsert("hic"));
        }

        try (var dict = new KeywordLexicon(createJournal(path.toFile()), new DictionaryHashMap(1L<<16))) {
            assertNotEquals(0, dict.getOrInsert("hoc"));
            assertEquals(0, dict.getOrInsert("hic"));
        }

        path.toFile().delete();
    }

    @SneakyThrows
    @Test
    void get() {
        var path = Files.createTempFile("dict", ".tmp");
        try (var dict = new KeywordLexicon(createJournal(path.toFile()), new DictionaryHashMap(1L<<16))) {
            dict.getOrInsert("hic");
            dict.getOrInsert("hac");
            dict.getOrInsert("haec");
            dict.getOrInsert("hoc");
            dict.commitToDisk();
            dict.getOrInsert("quae");
            dict.getOrInsert("quis");
            dict.getOrInsert("quem");
            dict.getOrInsert("cuis");
            dict.commitToDisk();
            assertNotEquals(0, dict.getOrInsert("hac"));
            assertEquals(0, dict.getOrInsert("hic"));
        }

        try (var dict = new KeywordLexicon(createJournal(path.toFile()), new DictionaryHashMap(1L<<16))) {
            assertNotEquals(0, dict.getOrInsert("hoc"));
            assertEquals(0, dict.getOrInsert("hic"));
        }

        path.toFile().delete();
    }

    @SneakyThrows
    @Test
    void getDoubleWrite() {
        var path = Files.createTempFile("dict", ".tmp");

        try (var dict = new KeywordLexicon(createJournal(path.toFile()), new DictionaryHashMap(1L<<16))) {
            dict.commitToDisk();
        }

        try (var dict = new KeywordLexicon(createJournal(path.toFile()), new DictionaryHashMap(1L<<16))) {
            dict.getOrInsert("hic");
            dict.getOrInsert("hac");
            dict.getOrInsert("haec");
            dict.getOrInsert("hoc");
            dict.getOrInsert("quae");
            dict.getOrInsert("quis");
            dict.getOrInsert("quem");
            dict.getOrInsert("cuis");
            dict.commitToDisk();
            assertNotEquals(0, dict.getOrInsert("hac"));
            assertEquals(0, dict.getOrInsert("hic"));
        }

        var dict = new KeywordLexiconReadOnlyView(new KeywordLexicon(createJournal(path.toFile()), new DictionaryHashMap(1L<<16)));

        assertNotEquals(0, dict.get("hoc"));
        assertEquals(0, dict.get("hic"));

        path.toFile().delete();
    }

    @SneakyThrows
    @Test
    void getDoubleWrite2() {
        var path = Files.createTempFile("dict", ".tmp");

        try (var dict = new KeywordLexicon(createJournal(path.toFile()), new DictionaryHashMap(1L<<16))) {
            dict.getOrInsert("hic");
            dict.getOrInsert("hac");
            dict.getOrInsert("haec");
            dict.getOrInsert("hoc");
            dict.getOrInsert("quae");
            dict.getOrInsert("quis");
            dict.getOrInsert("quem");
            dict.getOrInsert("cuis");
            dict.commitToDisk();
            assertNotEquals(0, dict.getOrInsert("hac"));
            assertEquals(0, dict.getOrInsert("hic"));
        }

        try (var dict = new KeywordLexicon(createJournal(path.toFile()), new DictionaryHashMap(1L<<16))) {
            dict.getOrInsert("fe");
            dict.getOrInsert("fi");
            dict.getOrInsert("fo");
            dict.getOrInsert("fum");
            dict.commitToDisk();
            assertNotEquals(0, dict.getOrInsert("hac"));
            assertEquals(0, dict.getOrInsert("hic"));
        }

        try (var dict = new KeywordLexicon(createJournal(path.toFile()), new DictionaryHashMap(1L<<16))) {
            dict.getOrInsert("bip");
            dict.getOrInsert("bap");
            dict.commitToDisk();
        }


        var dict = new KeywordLexiconReadOnlyView(new KeywordLexicon(createJournal(path.toFile()), new DictionaryHashMap(1L<<16)));

        assertEquals(0, dict.get("hic"));
        assertEquals(1, dict.get("hac"));
        assertEquals(2, dict.get("haec"));
        assertEquals(3, dict.get("hoc"));
        assertEquals(4, dict.get("quae"));
        assertEquals(5, dict.get("quis"));
        assertEquals(6, dict.get("quem"));
        assertEquals(7, dict.get("cuis"));
        assertEquals(8, dict.get("fe"));
        assertEquals(9, dict.get("fi"));
        assertEquals(10, dict.get("fo"));
        assertEquals(11, dict.get("fum"));
        assertEquals(12, dict.get("bip"));
        assertEquals(13, dict.get("bap"));
        path.toFile().delete();
    }

}