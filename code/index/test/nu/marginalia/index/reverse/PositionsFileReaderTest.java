package nu.marginalia.index.reverse;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.index.reverse.construction.PositionsFileConstructor;
import nu.marginalia.index.reverse.positions.PositionsFileReader;
import nu.marginalia.index.reverse.query.IndexSearchBudget;
import nu.marginalia.sequence.CodedSequence;
import nu.marginalia.sequence.VarintCodedSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;

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
    void getTermData() throws IOException, TimeoutException {
        long key1, key2, key3;
        try (PositionsFileConstructor constructor = new PositionsFileConstructor(file)) {
            var block = constructor.getBlock();
            key1 = constructor.add(block, VarintCodedSequence.generate(1, 2, 3).buffer());
            key2 = constructor.add(block, VarintCodedSequence.generate(2, 3, 5, 1000, 5000, 20241).buffer());
            key3 = constructor.add(block, VarintCodedSequence.generate(3, 5, 7).buffer());
            block.commit();
        }

        System.out.println("key1: " + Long.toHexString(key1));
        System.out.println("key2: " + Long.toHexString(key2));
        System.out.println("key3: " + Long.toHexString(key3));

        try (Arena arena = Arena.ofShared();
            PositionsFileReader reader = new PositionsFileReader(file))
        {
            CodedSequence[] data = null; // FIXME reader.getTermData(arena, new IndexSearchBudget(10000), new long[] { key1, key2, key3 });

            assertEquals(IntList.of( 1, 2, 3), data[0].values());
            assertEquals(IntList.of(2, 3, 5, 1000, 5000, 20241), data[1].values());
            assertEquals(IntList.of(3, 5, 7), data[2].values());
        }
    }
}