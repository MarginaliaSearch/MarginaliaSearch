package nu.marginalia.wmsa.edge.index.service;

import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.service.dictionary.DictionaryWriter;
import nu.marginalia.wmsa.edge.index.service.index.SearchIndex;
import nu.marginalia.wmsa.edge.index.service.index.SearchIndexConverter;
import nu.marginalia.wmsa.edge.index.service.index.SearchIndexReader;
import nu.marginalia.wmsa.edge.index.service.index.SearchIndexWriterImpl;
import nu.marginalia.wmsa.edge.index.service.query.IndexSearchBudget;
import nu.marginalia.wmsa.edge.index.service.query.SearchIndexPartitioner;
import nu.marginalia.wmsa.edge.model.EdgeId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;

import static nu.marginalia.util.dict.DictionaryHashMap.NO_VALUE;
import static org.junit.jupiter.api.Assertions.*;

class SearchIndexWriterTest {
    DictionaryWriter dictionaryWriter;
    SearchIndexWriterImpl writer;

    Path indexFile;
    Path wordsFile1;
    Path urlsFile1;
    Path dictionaryFile;

    @BeforeEach @SneakyThrows
    void setUp() {
        dictionaryFile = Files.createTempFile("tmp", ".dict");
        dictionaryFile.toFile().deleteOnExit();

        dictionaryWriter = new DictionaryWriter(dictionaryFile.toFile(), 1L<<16, false);

        indexFile = Files.createTempFile("tmp", ".idx");
        indexFile.toFile().deleteOnExit();
        writer = new SearchIndexWriterImpl(dictionaryWriter, indexFile.toFile());

        wordsFile1 = Files.createTempFile("words1", ".idx");
        urlsFile1 = Files.createTempFile("urls1", ".idx");
    }

    @SneakyThrows
    @AfterEach
    void tearDown() {
        dictionaryWriter.close();
        writer.close();
        indexFile.toFile().delete();
        dictionaryFile.toFile().delete();
        urlsFile1.toFile().delete();
        wordsFile1.toFile().delete();
    }

    public long[] findWord(SearchIndexReader reader, String word, IndexBlock block) {
        IndexSearchBudget budget = new IndexSearchBudget(100);
        return reader.findWord(block, budget, lv->true, dictionaryWriter.getReadOnly(word)).stream().toArray();
    }

    @Test @SneakyThrows
    void put() throws IOException {
        writer.put(new EdgeId<>(0), new EdgeId<>(1), IndexBlock.Words, Arrays.asList("Hello", "Salvete", "everyone!", "This", "is", "Bob"));
        writer.put(new EdgeId<>(0), new EdgeId<>(2), IndexBlock.Words, Arrays.asList("Salvete", "omnes!", "Bob", "sum", "Hello"));
        writer.forceWrite();

        new SearchIndexConverter(IndexBlock.Words, 0, Path.of("/tmp"), indexFile.toFile(), wordsFile1.toFile(), urlsFile1.toFile(), new SearchIndexPartitioner(null), val -> false);

        EnumMap<IndexBlock, SearchIndex> indices = new EnumMap<IndexBlock, SearchIndex>(IndexBlock.class);
        indices.put(IndexBlock.Words, new SearchIndex("0", urlsFile1.toFile(), wordsFile1.toFile()));

        var reader = new SearchIndexReader(indices);

        int bobId = dictionaryWriter.getReadOnly("Bob");
        assertNotEquals(NO_VALUE, bobId);

        assertEquals(2, reader.numHits(IndexBlock.Words, bobId));
        assertArrayEquals(new long[] { 1, 2 }, findWord(reader,"Bob", IndexBlock.Words));
        assertArrayEquals(new long[] { 2 }, findWord(reader,"sum", IndexBlock.Words));
        assertArrayEquals(new long[] { }, findWord(reader,"New Word", IndexBlock.Words));

        writer.close();
    }

}