package nu.marginalia.index;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.index.construction.PositionsFileConstructor;
import nu.marginalia.index.positions.TermData;
import nu.marginalia.index.positions.PositionsFileReader;
import nu.marginalia.sequence.GammaCodedSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PositionsFileReaderTest {

    Path file;

    @BeforeEach
    void setUp() throws IOException {
        file = Files.createTempFile("positions", "dat");
    }
    @AfterEach
    void tearDown() throws IOException {
        Files.delete(file);
    }

    @Test
    void getTermData() throws IOException {
        ByteBuffer workArea = ByteBuffer.allocate(8192);
        long key1, key2, key3;
        try (PositionsFileConstructor constructor = new PositionsFileConstructor(file)) {
            key1 = constructor.add((byte) 43, GammaCodedSequence.generate(workArea, 1, 2, 3));
            key2 = constructor.add((byte) 51, GammaCodedSequence.generate(workArea, 2, 3, 5, 1000, 5000, 20241));
            key3 = constructor.add((byte) 61, GammaCodedSequence.generate(workArea, 3, 5, 7));
        }

        System.out.println("key1: " + Long.toHexString(key1));
        System.out.println("key2: " + Long.toHexString(key2));
        System.out.println("key3: " + Long.toHexString(key3));

        try (Arena arena = Arena.ofConfined();
            PositionsFileReader reader = new PositionsFileReader(file))
        {
            TermData data1 = reader.getTermData(arena, key1);
            assertEquals(43, data1.flags());
            assertEquals(IntList.of( 1, 2, 3), data1.positions().values());

            TermData data2 = reader.getTermData(arena, key2);
            assertEquals(51, data2.flags());
            assertEquals(IntList.of(2, 3, 5, 1000, 5000, 20241), data2.positions().values());

            TermData data3 = reader.getTermData(arena, key3);
            assertEquals(61, data3.flags());
            assertEquals(IntList.of(3, 5, 7), data3.positions().values());
        }
    }
}