package nu.marginalia.index;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.index.construction.PositionsFileConstructor;
import nu.marginalia.index.positions.PositionsFileReader;
import nu.marginalia.index.positions.TermData;
import nu.marginalia.sequence.VarintCodedSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        long key1, key2, key3;
        try (PositionsFileConstructor constructor = new PositionsFileConstructor(file)) {
            key1 = constructor.add((byte) 43, VarintCodedSequence.generate(1, 2, 3).buffer());
            key2 = constructor.add((byte) 51, VarintCodedSequence.generate(2, 3, 5, 1000, 5000, 20241).buffer());
            key3 = constructor.add((byte) 61, VarintCodedSequence.generate(3, 5, 7).buffer());
        }

        System.out.println("key1: " + Long.toHexString(key1));
        System.out.println("key2: " + Long.toHexString(key2));
        System.out.println("key3: " + Long.toHexString(key3));

        try (Arena arena = Arena.ofShared();
            PositionsFileReader reader = new PositionsFileReader(file))
        {
            TermData[] data = reader.getTermData(arena, new long[] { key1, key2, key3 });

            assertEquals(43, data[0].flags());
            assertEquals(IntList.of( 1, 2, 3), data[0].positions().values());

            assertEquals(51, data[1].flags());
            assertEquals(IntList.of(2, 3, 5, 1000, 5000, 20241), data[1].positions().values());

            assertEquals(61, data[2].flags());
            assertEquals(IntList.of(3, 5, 7), data[2].positions().values());
        }
    }
}