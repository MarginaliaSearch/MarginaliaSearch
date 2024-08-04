package nu.marginalia.index.forward;

import nu.marginalia.index.forward.spans.ForwardIndexSpansReader;
import nu.marginalia.index.forward.spans.ForwardIndexSpansWriter;
import nu.marginalia.sequence.GammaCodedSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ForwardIndexSpansReaderTest {
    Path testFile = Files.createTempFile("test", ".idx");

    ForwardIndexSpansReaderTest() throws IOException {
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(testFile);
    }

    @Test
    void testSunnyDay() throws IOException {
        ByteBuffer wa = ByteBuffer.allocate(32);

        long offset1;
        long offset2;
        try (var writer = new ForwardIndexSpansWriter(testFile)) {
            writer.beginRecord(1);
            writer.writeSpan((byte) 'h', GammaCodedSequence.generate(wa, 1, 3, 5, 8).buffer());
            offset1 = writer.endRecord();

            writer.beginRecord(2);
            writer.writeSpan((byte) 'c', GammaCodedSequence.generate(wa, 2, 4, 6, 7).buffer());
            writer.writeSpan((byte) 'a', GammaCodedSequence.generate(wa, 3, 5).buffer());
            offset2 = writer.endRecord();
        }

        try (var reader = new ForwardIndexSpansReader(testFile);
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

            assertEquals(1, spans2.anchor.size());

            assertEquals(0, spans2.title.size());
            assertFalse(spans2.title.containsPosition(8));
        }
    }
}