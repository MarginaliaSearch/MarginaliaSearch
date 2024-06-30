The coded-sequence library offers tools for encoding sequences 
of integers with a variable-length encoding. 

The Elias Gamma code is supported:
https://en.wikipedia.org/wiki/Elias_gamma_coding

The `GammaCodedSequence` class stores a sequence of ascending
non-negative integers in a byte buffer.  The encoding also
stores the length of the sequence (as a gamma-coded value), 
which is used in decoding.

Sequences are encoded with the `GammaCodedSequence.of()`-method,
and require a temporary buffer to work in.
```java
// allocate a temporary buffer to work in, this is reused
// for all operations and will not hold the final result
ByteBuffer workArea = ByteBuffer.allocate(1024);

// create a new GammaCodedSequence with the given values
var gcs = GammaCodedSequence.of(workArea, 1, 3, 4, 7, 10);
```

The `GammaCodedSequence` class provides methods to query the
sequence, iterate over the values, and access the underlying
binary representation. 

```java
// query the sequence 
int valueCount = gcs.valueCount();
int bufferSize = gcs.bufferSize();

// iterate over the values
IntIterator iter = gcs.iterator();
IntList values = gcs.values();

// access the underlying data (e.g. for writing)
byte[] bytes = gcs.bytes();
ByteBuffer buffer = gcs.buffer();
```

The `GammaCodedSequence` class also provides methods to decode
a sequence from a byte buffer or byte array.

```java
// decode the data
var decodedGcs1 = new GammaCodedSequence(buffer);
var decodedGcs2 = new GammaCodedSequence(buffer, start, end);
var decodedGcs3 = new GammaCodedSequence(bytes);
```