# Random Write Funnel

This micro-library solves the problem of [write amplification](https://en.wikipedia.org/wiki/Write_amplification) when
writing large files out of order to disk.  It does this by bucketing the writes into several temporary files,
which are then evaluated to construct the larger file with a more predictable order of writes.

Even though it effectively writes 2.5x as much data to disk than simply attempting to 
construct the file directly, it is *much* faster than thrashing an SSD with dozens of gigabytes
of small random writes.

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

* [RandomWriteFunnel](src/main/java/nu/marginalia/rwf/RandomWriteFunnel.java)