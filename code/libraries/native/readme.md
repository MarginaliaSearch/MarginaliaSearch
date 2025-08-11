# Native C++ Helpers

This package contains helper functions for calling native functions.

### Systems Programming Helpers

TBW

### Long Array Helpers. 

The helpers are only built on Linux, and if they are absent, Java substitutes should be used instead.  

Library loading and access is available through the
[NativeAlgos](java/nu/marginalia/NativeAlgos.java) class.

Note that the C++ helpers are compiled with march=native by default, so they are not portable 
between different CPU architectures.

This provides a speedup for some operations, especially when compared to using MemorySegment's
get methods and not Unsafe.

## Benchmarks

JMH benchmark results for 64 bit quicksort N=2^10, 25 trials, graalvm 21.0.3:

| Implementation | Throughput ops/s | Error p=99% |
|----------------|------------------|-------------|
| native         | 114.877          | 4.216       |
| unsafe         | 98.443           | 2.723       |
| memorysegment  | 72.492           | 1.980       |

Note that odds are stacked in favor of the native implementation 
since the dataset is fairly large, meaning the call overhead is 
less significant.  For smaller datasets, the overhead of native 
calls may be a bigger factor.