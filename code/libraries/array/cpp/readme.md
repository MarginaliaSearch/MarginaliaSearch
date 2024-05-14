# Array C++ Helpers

This package contains helper functions for working with LongArray objects,
as native C++ calls.  The helpers are only built on Linux, and if they are absent,
Java substitutes should be used instead.  

Note that the C++ helpers are compiled with march=native, so they are not portable 
between different CPU architectures.

This provides a speedup for some operations, especially when using MemorySegment's
get methods and not Unsafe.  Library loading and access is available through the 
[NativeAlgos](java/nu/marginalia/NativeAlgos.java) class.
