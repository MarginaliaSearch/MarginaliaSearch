package nu.marginalia.wmsa.edge.index.service;

import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.dictionary.DictionaryReader;
import nu.marginalia.wmsa.edge.index.dictionary.DictionaryWriter;
import nu.marginalia.wmsa.edge.index.service.index.SearchIndexConverter;
import nu.marginalia.wmsa.edge.index.service.query.SearchIndexPartitioner;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.*;
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
    @SneakyThrows
    @Test
    @Disabled
    void test() {
        try (var dict = new DictionaryWriter(Path.of("/home/vlofgren/Code/data/dictionary.dat").toFile(), 1L<<16, false)) {
            wait();
        }
    }


    @SneakyThrows
    @Test
    void getFold() {
        var path = Files.createTempFile("dict", ".tmp");
        try (var dict = new DictionaryWriter(path.toFile(), 1L<<16, false)) {
            dict.get("hic");
            dict.get("hac");
            dict.commitToDisk();
            dict.get("quae");
            dict.get("quis");
            dict.get("quem1");
            dict.get("quem2");
            dict.get("quem3");
            dict.get("quem4");
            dict.get("quem5");
            dict.get("quem6");
            dict.get("quem7");
            dict.get("quem8");
            dict.get("quem9");
            dict.get("quem10");
            dict.get("cuis");
            dict.get("haec_hic");
            dict.get("hoc_hac_cuis");
            dict.commitToDisk();
            assertNotEquals(0, dict.get("hac"));
            assertEquals(0, dict.get("hic"));
        }

        try (var dict = new DictionaryWriter(path.toFile(), 1L<<16, false)) {
            assertNotEquals(0, dict.get("hoc"));
            assertEquals(0, dict.get("hic"));
        }

        path.toFile().delete();
    }

    @SneakyThrows
    @Test
    void get() {
        var path = Files.createTempFile("dict", ".tmp");
        try (var dict = new DictionaryWriter(path.toFile(), 1L<<16, false)) {
            dict.get("hic");
            dict.get("hac");
            dict.get("haec");
            dict.get("hoc");
            dict.commitToDisk();
            dict.get("quae");
            dict.get("quis");
            dict.get("quem");
            dict.get("cuis");
            dict.commitToDisk();
            assertNotEquals(0, dict.get("hac"));
            assertEquals(0, dict.get("hic"));
        }

        try (var dict = new DictionaryWriter(path.toFile(), 1L<<16, false)) {
            assertNotEquals(0, dict.get("hoc"));
            assertEquals(0, dict.get("hic"));
        }

        path.toFile().delete();
    }

    @SneakyThrows
    @Test
    void getDoubleWrite() {
        var path = Files.createTempFile("dict", ".tmp");

        try (var dict = new DictionaryWriter(path.toFile(), 1L<<16, false)) {
            dict.commitToDisk();
        }

        try (var dict = new DictionaryWriter(path.toFile(), 1L<<16, false)) {
            dict.get("hic");
            dict.get("hac");
            dict.get("haec");
            dict.get("hoc");
            dict.get("quae");
            dict.get("quis");
            dict.get("quem");
            dict.get("cuis");
            dict.commitToDisk();
            assertNotEquals(0, dict.get("hac"));
            assertEquals(0, dict.get("hic"));
        }

        var dict = new DictionaryReader(new DictionaryWriter(path.toFile(), 1L<<16, false));

        assertNotEquals(0, dict.get("hoc"));
        assertEquals(0, dict.get("hic"));

        path.toFile().delete();
    }

    @SneakyThrows
    @Test
    void getDoubleWrite2() {
        var path = Files.createTempFile("dict", ".tmp");

        try (var dict = new DictionaryWriter(path.toFile(), 1L<<16, false)) {
            dict.get("hic");
            dict.get("hac");
            dict.get("haec");
            dict.get("hoc");
            dict.get("quae");
            dict.get("quis");
            dict.get("quem");
            dict.get("cuis");
            dict.commitToDisk();
            assertNotEquals(0, dict.get("hac"));
            assertEquals(0, dict.get("hic"));
        }

        try (var dict = new DictionaryWriter(path.toFile(), 1L<<16, false)) {
            dict.get("fe");
            dict.get("fi");
            dict.get("fo");
            dict.get("fum");
            dict.commitToDisk();
            assertNotEquals(0, dict.get("hac"));
            assertEquals(0, dict.get("hic"));
        }

        try (var dict = new DictionaryWriter(path.toFile(), 1L<<16, false)) {
            dict.get("bip");
            dict.get("bap");
            dict.commitToDisk();
        }


        var dict = new DictionaryReader(new DictionaryWriter(path.toFile(), 1L<<16, false));

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