# Array Library

The array library offers easy allocation of large memory mapped files with less 
performance overhead than the traditional `buffers[i].get(j)`-style constructions
java often leads to due to its ByteBuffer size limitation.

It's a very C++-style library that does unidiomatic things with interface default 
functions to get diamond inheritance.

# Quick demo:
```
var array =
        LongArray.mmapForWriting(Path.of("/tmp/test"), 1<<16);

array.transformEach(50, 1000, (pos, val) -> Long.hashCode(pos));
array.quickSort(50, 1000);
if (array.binarySearch(array.get(100), 50, 1000) >= 0) {
    System.out.println("Nevermind, I found it!");
}

array.range(50, 1000).fill(0, 950, 1);
array.forEach(0, 100, (pos, val) -> {
    System.out.println(pos + ":" + val);
});

```