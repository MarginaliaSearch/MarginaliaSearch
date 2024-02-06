This micro-library with strategies for solving the problem of [write amplification](https://en.wikipedia.org/wiki/Write_amplification) when
writing large files out of order to disk.  It offers a simple API to write data to a file in a 
random order, while localizing the writes.

Several strategies are available from the [RandomFileAssembler](src/main/java/nu/marginalia/rwf/RandomFileAssembler.java) 
interface.

* Writing to a memory mapped file (non-solution, for small files)
* Writing to a memory buffer (for systems with enough memory)
* [RandomWriteFunnel](src/main/java/nu/marginalia/rwf/RandomWriteFunnel.java) - Not bound by memory. 

The data is written in a native byte order.

## RandomWriteFunnel

The RandomWriteFunnel solves the problem by bucketing the writes into several temporary files,
which are then evaluated to construct the larger file with a more predictable order of writes.

Even though it effectively writes 2.5x as much data to disk than simply attempting to 
construct the file directly, it is *much* faster than thrashing an SSD with dozens of gigabytes
of small random writes, which is what tends to happen if you naively mmap a file that is larger
than the system RAM, and write to it in a random order.

## Demo
```java
try (var rfw = new RandomWriteFunnel(tmpPath, expectedSize);
     var out = Files.newByteChannel(outputFile, StandardOpenOption.WRITE)) 
{
    rwf.put(addr1, data1);
    rwf.put(addr2, data2);
    // ...
    rwf.put(addr1e33, data1e33);
    
    rwf.write(out);
}
catch (IOException ex) {
    //
}
```


## Central Classes

* [RandomFileAssembler](src/main/java/nu/marginalia/rwf/RandomFileAssembler.java)
* [RandomWriteFunnel](src/main/java/nu/marginalia/rwf/RandomWriteFunnel.java)