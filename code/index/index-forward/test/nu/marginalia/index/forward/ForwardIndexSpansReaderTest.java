package nu.marginalia.index.forward;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.sequence.GammaCodedSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
            writer.writeSpan((byte) 'a', GammaCodedSequence.generate(wa, 1, 3, 5).buffer());
            offset1 = writer.endRecord();

            writer.beginRecord(2);
            writer.writeSpan((byte) 'b', GammaCodedSequence.generate(wa, 2, 4, 6).buffer());
            writer.writeSpan((byte) 'c', GammaCodedSequence.generate(wa, 3, 5, 7).buffer());
            offset2 = writer.endRecord();
        }

        try (var reader = new ForwardIndexSpansReader(testFile);
             var arena = Arena.ofConfined()
        ) {
            var spans1 = reader.readSpans(arena, offset1);
            var spans2 = reader.readSpans(arena, offset2);

            assertEquals(1, spans1.size());

            assertEquals('a', spans1.get(0).code());
            assertEquals(IntList.of(1, 3, 5), spans1.get(0).data());

            assertEquals(2, spans2.size());

            assertEquals('b', spans2.get(0).code());
            assertEquals(IntList.of(2, 4, 6), spans2.get(0).data());
            assertEquals('c', spans2.get(1).code());
            assertEquals(IntList.of(3, 5, 7), spans2.get(1).data());
        }
    }
}