# Array Library

The array library offers easy allocation of large [memory mapped files](https://en.wikipedia.org/wiki/Memory-mapped_file)
and off-heap memory, along with helper functions for accessing and using such memory.

Historically this used ByteBuffers, but has been updated to use the new [MemorySegment](https://openjdk.org/jeps/454) 
API.  By default, it uses sun.misc.Unsafe to access the memory, but it can be configured to use the new MemorySegment access
methods instead by setting the system property `system.noSunMiscUnsafe` to true.  This is quite a bit slower, but 
use-after-free results in a harmless exception rather than a SIGSEGV.

Internally the array objects use Arena allocators to manage memory, and need to be closed to free the memory.  Both
confined and shared memory can be allocated, as per the MemorySegment API.

The library is implemented in a fairly unidiomatic way using interfaces to accomplish diamond inheritance. 

## Quick demo:
```java
try (var array = LongArrayFactory.mmapForWritingConfined(Path.of("/tmp/test"), 1<<16)) {
    array.transformEach(50, 1000, (pos, val) -> Long.hashCode(pos));
    array.quickSort(50, 1000);
    if (array.binarySearch(array.get(100), 50, 1000) >= 0) {
        System.out.println("Nevermind, I found it!");
    }
    
    array.range(50, 1000).fill(0, 950, 1);
    array.forEach(0, 100, (pos, val) -> {
        System.out.println(pos + ":" + val);
    });
}
```


## Query Buffers

The classes [IntQueryBuffer](java/nu/marginalia/array/buffer/IntQueryBuffer.java)
and [LongQueryBuffer](java/nu/marginalia/array/buffer/LongQueryBuffer.java) are used
heavily in the search engine's query processing.

They are dual-pointer buffers that offer tools for filtering data.

```java
LongQueryBuffer buffer = new LongQueryBuffer(1000);

// later ...

// Prepare the buffer for filling
buffer.reset();
fillBufferSomehow(buffer); 

// length is updated and data is set
// read pointer and write pointer is now at 0

// A typical filtering operation may look like this:
        
while (buffer.hasMore()) { // read < end
    if (someCondition(buffer.currentValue())) {
        // copy the value pointed to by the read
        // pointer to the read pointer, and
        // advance both
        buffer.retainAndAdvance();
    }
    else {
        // advance the read pointer
        buffer.rejectAndAdvance();
    }
}

// set end to the write pointer, and 
// resets the read and write pointers
buffer.finalizeFiltering();

// ... after this we can filter again, or
// consume the data
```


Especially noteworthy are the operations `retain()` and `reject()` in
[IntArraySearch](java/nu/marginalia/array/algo/IntArraySearch.java) and [LongArraySearch](java/nu/marginalia/array/algo/LongArraySearch.java).
They keep or remove all items in the buffer that exist in the referenced range of the array,
which must be sorted.

These are used to offer an intersection operation for the B-Tree with sub-linear run time.  
