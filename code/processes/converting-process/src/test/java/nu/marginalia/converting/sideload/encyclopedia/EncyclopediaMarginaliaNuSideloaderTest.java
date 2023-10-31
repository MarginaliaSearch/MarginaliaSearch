package nu.marginalia.converting.sideload.encyclopedia;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import gnu.trove.list.array.TLongArrayList;
import nu.marginalia.converting.ConverterModule;
import nu.marginalia.converting.model.DisqualifiedException;
import nu.marginalia.converting.processor.ConverterDomainTypes;
import nu.marginalia.converting.sideload.SideloaderProcessing;
import nu.marginalia.io.processed.DocumentRecordParquetFileReader;
import nu.marginalia.io.processed.DocumentRecordParquetFileWriter;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.model.processed.DocumentRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EncyclopediaMarginaliaNuSideloaderTest {
    Path tempFile;

    @BeforeEach
    public void setUp() throws IOException {
        tempFile = Files.createTempFile(getClass().getSimpleName(), ".dat");
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(tempFile);
    }

    @Test
    public void test() {
        System.out.printf("%64s\n", Long.toBinaryString(Long.reverseBytes(0x8fa302ffffcffebfL)));
        System.out.printf("%64s\n", Long.toBinaryString(Long.reverseBytes(0x8fa302ffffcffebfL)));
        System.out.printf("%64s\n", Long.toBinaryString(0xFAAFFFF7F75AA808L));

        System.out.printf("%64s\n", Long.toBinaryString(Long.reverseBytes(0xa00000L)));
        System.out.printf("%64s\n", Long.toBinaryString(0x20A00000000000L));

        System.out.printf("%64s\n", Long.toBinaryString(Long.reverseBytes(0x200000L)));
        System.out.printf("%64s\n", Long.toBinaryString(0x200000000004L));

        System.out.printf("%64s\n", Long.toBinaryString(Long.reverseBytes(0x1000000000000000L)));
        System.out.printf("%64s\n", Long.toBinaryString(0x10L));
    }
    @Test
    public void debugSpecificArticle() throws SQLException, IOException, URISyntaxException, DisqualifiedException {
        Path pathToDbFile = Path.of("/home/vlofgren/Code/MarginaliaSearch/run/samples/articles.db");
        if (!Files.exists(pathToDbFile)) {
            // not really practical to ship a 40 Gb sqlite files on github
            // be @vlofgren to run this test
            return;
        }
        var domainTypesMock = Mockito.mock(ConverterDomainTypes.class);
        Mockito.when(domainTypesMock.isBlog(Mockito.any())).thenReturn(false);
        var processing = Guice.createInjector(new ConverterModule(),
                new AbstractModule() {
                    public void configure() {
                        bind(ConverterDomainTypes.class).toInstance(domainTypesMock);
                    }
                }
            )
                .getInstance(SideloaderProcessing.class);

        var sideloader = new EncyclopediaMarginaliaNuSideloader(
                pathToDbFile,
                "https://en.wikipedia.org/wiki/",
                GsonFactory.get(),
                processing
        );

        var document = sideloader.processJust("Don't_Tell_Me_(Madonna_song)");

        System.out.println(document);

        var keywordsBuilt = document.words.build();

        var ptr = keywordsBuilt.newPointer();

        Map<String, WordMetadata> dirtyAndBlues = new HashMap<>();

        while (ptr.advancePointer()) {
            String word = ptr.getKeyword();

            System.out.println(word + ": " + Long.toHexString(Long.reverseBytes(ptr.getMetadata())));

            if (Set.of("dirty", "blues").contains(word)) {
                WordMetadata meta = new WordMetadata(ptr.getMetadata());

                Assertions.assertNull(
                        dirtyAndBlues.put(word, meta)
                );
            }
        }

        Assertions.assertTrue(dirtyAndBlues.containsKey("dirty"));
        Assertions.assertTrue(dirtyAndBlues.containsKey("blues"));
        Assertions.assertNotEquals(
                dirtyAndBlues.get("dirty"),
                dirtyAndBlues.get("blues")
        );

        try (var dw = new DocumentRecordParquetFileWriter(tempFile)) {
            dw.write(new DocumentRecord(
                    "encyclopedia.marginalia.nu",
                    document.url.toString(),
                    0,
                    document.state.toString(),
                    document.stateReason,
                    document.details.title,
                    document.details.description,
                    HtmlFeature.encode(document.details.features),
                    document.details.standard.name(),
                    document.details.length,
                    document.details.hashCode,
                    (float) document.details.quality,
                    document.details.metadata.encode(),
                    document.details.pubYear,
                    List.of(keywordsBuilt.keywords),
                    new TLongArrayList(keywordsBuilt.metadata)
            ));
        }

        var record = DocumentRecordParquetFileReader.streamKeywordsProjection(tempFile).findFirst().get();
        String[] words = record.words.toArray(String[]::new);
        long[] meta = record.metas.toArray();

        assertArrayEquals(keywordsBuilt.keywords, words);
        assertArrayEquals(keywordsBuilt.metadata, meta);
    }
}