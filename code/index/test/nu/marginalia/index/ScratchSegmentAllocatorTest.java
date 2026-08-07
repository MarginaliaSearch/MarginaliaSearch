package nu.marginalia.index;

import org.junit.jupiter.api.Test;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.*;

class ScratchSegmentAllocatorTest {

    @Test
    void testAllocationsAreDisjointAndAligned() {
        var factory = new ScratchSegmentAllocatorFactory("test", 1024);

        try (var allocator = factory.createAllocator()) {
            MemorySegment a = allocator.allocate(100, 8);
            MemorySegment b = allocator.allocate(100, 8);

            assertEquals(100, a.byteSize());
            assertEquals(100, b.byteSize());
            assertEquals(0, a.address() % 8);
            assertEquals(0, b.address() % 8);

            a.fill((byte) 0xAA);
            b.fill((byte) 0xBB);

            assertEquals((byte) 0xAA, a.get(ValueLayout.JAVA_BYTE, 99));
            assertEquals((byte) 0xBB, b.get(ValueLayout.JAVA_BYTE, 0));
        }
    }

    @Test
    void testOverflowAllocationsAreServed() {
        var factory = new ScratchSegmentAllocatorFactory("test", 128);

        try (var allocator = factory.createAllocator()) {
            MemorySegment small = allocator.allocate(64, 8);
            MemorySegment large = allocator.allocate(1024, 8);

            small.fill((byte) 1);
            large.fill((byte) 2);

            assertEquals((byte) 1, small.get(ValueLayout.JAVA_BYTE, 63));
            assertEquals((byte) 2, large.get(ValueLayout.JAVA_BYTE, 1023));
        }
    }

    @Test
    void testSlabSizeIsFixed() {
        var factory = new ScratchSegmentAllocatorFactory("test", 128);

        try (var allocator = factory.createAllocator()) {
            allocator.allocate(1024, 8);
            allocator.reset();

            assertEquals(128, allocator.slabSize());
        }
    }

    @Test
    void testResetReusesSlab() {
        var factory = new ScratchSegmentAllocatorFactory("test", 1024);

        try (var allocator = factory.createAllocator()) {
            MemorySegment first = allocator.allocate(512, 8);
            allocator.reset();
            MemorySegment second = allocator.allocate(512, 8);

            assertEquals(first.address(), second.address());
        }
    }

    @Test
    void testCloseReturnsAllocatorToPool() {
        var factory = new ScratchSegmentAllocatorFactory("test", 1024);

        var first = factory.createAllocator();
        first.allocate(512, 8);
        first.close();

        try (var second = factory.createAllocator()) {
            assertSame(first, second);

            // The lease starts with a rewound slab
            MemorySegment segment = second.allocate(1024, 8);
            assertEquals(1024, segment.byteSize());
        }
    }

    @Test
    void testPoolServesConcurrentLeases() {
        var factory = new ScratchSegmentAllocatorFactory("test", 1024);

        var first = factory.createAllocator();
        var second = factory.createAllocator();

        assertNotSame(first, second);

        first.close();
        second.close();
    }

    @Test
    void testLargeAlignmentFallsBackToOverflow() {
        var factory = new ScratchSegmentAllocatorFactory("test", 1024);

        try (var allocator = factory.createAllocator()) {
            MemorySegment segment = allocator.allocate(64, 64);
            assertEquals(0, segment.address() % 64);
        }
    }

    @Test
    void testManyAllocationCycles() {
        var factory = new ScratchSegmentAllocatorFactory("test", 256);

        try (var allocator = factory.createAllocator()) {
            for (int cycle = 0; cycle < 1000; cycle++) {
                for (int i = 0; i < 8; i++) {
                    MemorySegment segment = allocator.allocate(100, 8);
                    segment.fill((byte) cycle);
                }
                allocator.reset();
            }
        }
    }
}
