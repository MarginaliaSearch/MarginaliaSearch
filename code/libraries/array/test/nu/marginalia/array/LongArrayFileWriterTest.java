package nu.marginalia.array;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class LongArrayFileWriterTest {
    Path file;

    @BeforeEach
    void setUp() throws IOException {
        file = Files.createTempFile(getClass().getSimpleName(), ".dat");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(file);
    }

    @Test
    void testPutSingleValues() throws IOException {
        try (var writer = LongArrayFileWriter.create(file)) {
            for (int i = 0; i < 3_000_000; i++) {
                writer.put(i);
            }
        }

        try (var array = LongArrayFactory.mmapForReadingConfined(file)) {
            assertEquals(3_000_000, array.size());
            for (int i = 0; i < array.size(); i++) {
                assertEquals(i, array.get(i));
            }
        }
    }

    @Test
    void testPutArraysNativeAndHeap() throws IOException {
        long[] heapValues = new long[5000];
        for (int i = 0; i < heapValues.length; i++) {
            heapValues[i] = -i;
        }

        // Larger than the writer's internal buffer
        int nativeSize = 2_500_000;

        try (var writer = LongArrayFileWriter.create(file);
             var nativeArray = LongArrayFactory.onHeapConfined(nativeSize))
        {
            for (int i = 0; i < nativeSize; i++) {
                nativeArray.set(i, 2L*i);
            }

            writer.put(LongArrayFactory.wrap(MemorySegment.ofArray(heapValues)), 100, 200);
            writer.put(nativeArray, 0, nativeArray.size());
            writer.put(nativeArray, 10, 20);
        }

        try (var array = LongArrayFactory.mmapForReadingConfined(file)) {
            assertEquals(100 + nativeSize + 10, array.size());

            for (int i = 0; i < 100; i++) {
                assertEquals(-(100 + i), array.get(i));
            }
            for (int i = 0; i < nativeSize; i++) {
                assertEquals(2L*i, array.get(100 + i));
            }
            for (int i = 0; i < 10; i++) {
                assertEquals(2L*(10 + i), array.get(100 + nativeSize + i));
            }
        }
    }


    @Test
    void testPutWithoutMemorySegment() throws IOException {
        LongArray source = mock(LongArray.class);

        when(source.hasMemorySegment()).thenReturn(false);
        when(source.size()).thenReturn(150_010L);
        when(source.get(anyLong())).thenAnswer(call -> -((long) call.getArgument(0)));

        try (var writer = LongArrayFileWriter.create(file)) {
            writer.put(123);
            writer.put(source, 10, 150_010);
            writer.put(source, source.size(), source.size());
            writer.put(456);
            assertThrows(IndexOutOfBoundsException.class, () -> writer.put(source, -1, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> writer.put(source, 0, source.size() + 1));
            assertThrows(IllegalArgumentException.class, () -> writer.put(source, 2, 1));
        }

        verify(source, never()).getMemorySegment();

        try (var array = LongArrayFactory.mmapForReadingConfined(file)) {
            assertEquals(150_002, array.size());
            assertEquals(123, array.get(0));
            for (int i = 0; i < 150_000; i++) {
                assertEquals(-(10L + i), array.get(1 + i));
            }
            assertEquals(456, array.get(150_001));
        }
    }

}
