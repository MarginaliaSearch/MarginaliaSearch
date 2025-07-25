package nu.marginalia.index.forward;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.index.forward.spans.IndexSpansReader;
import nu.marginalia.index.forward.spans.IndexSpansReaderPlain;
import nu.marginalia.index.forward.spans.IndexSpansWriter;
import nu.marginalia.language.sentence.tag.HtmlTag;
import nu.marginalia.sequence.VarintCodedSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class IndexSpansReaderTest {
    Path testFile = Files.createTempFile("test", ".idx");

    IndexSpansReaderTest() throws IOException {
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(testFile);
    }

    @Test
    void testContainsPosition() throws IOException {
        ByteBuffer wa = ByteBuffer.allocate(32);

        long offset1;
        long offset2;
        try (var writer = new IndexSpansWriter(testFile)) {
            writer.beginRecord(1);
            writer.writeSpan(HtmlTag.HEADING.code, VarintCodedSequence.generate(1, 3, 5, 8).buffer());
            offset1 = writer.endRecord();

            writer.beginRecord(3);
            writer.writeSpan(HtmlTag.CODE.code, VarintCodedSequence.generate(2, 4, 6, 7).buffer());
            writer.writeSpan(HtmlTag.ANCHOR.code, VarintCodedSequence.generate(3, 5).buffer());
            writer.writeSpan(HtmlTag.NAV.code, VarintCodedSequence.generate(1, 3).buffer());
            offset2 = writer.endRecord();
        }

        try (var reader = IndexSpansReader.open(testFile);
             var arena = Arena.ofConfined()
        ) {
            var spans1 = reader.readSpans(arena, offset1);
            var spans2 = reader.readSpans(arena, offset2);

            assertEquals(2, spans1.heading.size());

            assertEquals(2, spans2.code.size());

            assertFalse(spans2.code.containsPosition(1));
            assertTrue(spans2.code.containsPosition(3));
            assertFalse(spans2.code.containsPosition(5));
            assertTrue(spans2.code.containsPosition(6));
            assertFalse(spans2.code.containsPosition(7));
            assertFalse(spans2.code.containsPosition(8));

            assertTrue(spans2.nav.containsRange(IntList.of(1), 2));
            assertTrue(spans2.nav.containsRange(IntList.of(2), 1));
            assertTrue(spans2.nav.containsPosition(1));

            assertEquals(1, spans2.anchor.size());

            assertEquals(0, spans2.title.size());
            assertFalse(spans2.title.containsPosition(8));
        }
    }

    @Test
    void testContainsRange() throws IOException {
        long offset1;
        try (var writer = new IndexSpansWriter(testFile)) {
            writer.beginRecord(1);
            writer.writeSpan(HtmlTag.HEADING.code, VarintCodedSequence.generate( 1, 2, 10, 15, 20, 25).buffer());
            offset1 = writer.endRecord();
        }

        try (var reader = new IndexSpansReaderPlain(testFile);
             var arena = Arena.ofConfined()
        ) {
            var spans1 = reader.readSpans(arena, offset1);

            assertTrue(spans1.heading.containsRange(IntList.of(10), 2));
            assertTrue(spans1.heading.containsRange(IntList.of(8, 10), 2));
            assertTrue(spans1.heading.containsRange(IntList.of(8, 10, 14), 2));

            assertTrue(spans1.heading.containsRange(IntList.of(10), 5));
            assertTrue(spans1.heading.containsRange(IntList.of(8, 10), 5));
            assertTrue(spans1.heading.containsRange(IntList.of(8, 10, 14), 5));

            assertFalse(spans1.heading.containsRange(IntList.of(11), 5));
            assertFalse(spans1.heading.containsRange(IntList.of(9), 5));
        }
    }

    @Test
    void testContainsRangeExact() throws IOException {
        long offset1;
        try (var writer = new IndexSpansWriter(testFile)) {
            writer.beginRecord(1);
            writer.writeSpan(HtmlTag.HEADING.code, VarintCodedSequence.generate( 1, 2, 10, 15, 20, 25).buffer());
            offset1 = writer.endRecord();
        }

        try (var reader = new IndexSpansReaderPlain(testFile);
             var arena = Arena.ofConfined()
        ) {
            var spans1 = reader.readSpans(arena, offset1);

            assertEquals(0, spans1.heading.containsRangeExact(IntList.of(10), 2));
            assertEquals(0, spans1.heading.containsRangeExact(IntList.of(8, 10), 2));
            assertEquals(0, spans1.heading.containsRangeExact(IntList.of(8, 10, 14), 2));

            assertEquals(1, spans1.heading.containsRangeExact(IntList.of(10), 5));
            assertEquals(1, spans1.heading.containsRangeExact(IntList.of(8, 10), 5));
            assertEquals(1, spans1.heading.containsRangeExact(IntList.of(8, 10, 14), 5));

            assertEquals(0, spans1.heading.containsRangeExact(IntList.of(11), 5));
            assertEquals(0, spans1.heading.containsRangeExact(IntList.of(9), 5));
        }
    }

    @Test
    void testCountRangeMatches() throws IOException {
        long offset1;
        try (var writer = new IndexSpansWriter(testFile)) {
            writer.beginRecord(1);
            writer.writeSpan(HtmlTag.HEADING.code, VarintCodedSequence.generate( 1, 2, 10, 15, 20, 25).buffer());
            offset1 = writer.endRecord();
        }

        try (var reader = new IndexSpansReaderPlain(testFile);
             var arena = Arena.ofConfined()
        ) {
            var spans1 = reader.readSpans(arena, offset1);

            Assertions.assertEquals(1, spans1.heading.countRangeMatches(IntList.of(10), 2));
            Assertions.assertEquals(1, spans1.heading.countRangeMatches(IntList.of(8, 10), 2));
            Assertions.assertEquals(1, spans1.heading.countRangeMatches(IntList.of(8, 10, 14), 2));

            Assertions.assertEquals(1, spans1.heading.countRangeMatches(IntList.of(10), 5));
            Assertions.assertEquals(1, spans1.heading.countRangeMatches(IntList.of(8, 10), 5));
            Assertions.assertEquals(1, spans1.heading.countRangeMatches(IntList.of(8, 10, 14), 5));

            Assertions.assertEquals(2, spans1.heading.countRangeMatches(IntList.of(10, 20), 5));
            Assertions.assertEquals(2, spans1.heading.countRangeMatches(IntList.of(8, 10, 13, 20), 5));
            Assertions.assertEquals(2, spans1.heading.countRangeMatches(IntList.of(8, 10, 14, 20, 55), 5));

            Assertions.assertEquals(2, spans1.heading.countRangeMatches(IntList.of(10, 12), 2));

            Assertions.assertEquals(0, spans1.heading.countRangeMatches(IntList.of(11), 5));
            Assertions.assertEquals(0, spans1.heading.countRangeMatches(IntList.of(9), 5));
        }
    }
}