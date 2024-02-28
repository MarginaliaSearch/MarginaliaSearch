# BTree

This package contains a small library for creating and reading a static b-tree in as implicit pointer-less datastructure.
Both binary indices (i.e. sets) are supported, as well as arbitrary multiple-of-keysize key-value mappings where the data is 
interlaced with the keys in the leaf nodes. This is a fairly low-level datastructure. 

The b-trees are specified through a [BTreeContext](java/nu/marginalia/btree/model/BTreeContext.java)
which contains information about the data and index layout.

The b-trees are written through a [BTreeWriter](java/nu/marginalia/btree/BTreeWriter.java) and 
read with a [BTreeReader](java/nu/marginalia/btree/BTreeReader.java). 

## Demo

```java
BTreeContext ctx = new BTreeContext(
        4,  // num layers max
        1,  // entry size, 1 = the leaf node has just just the key
        BTreeBlockSize.BS_4096); // page size

// Allocate a memory area to work in, see the array library for how to do this with files
LongArray array = LongArray.allocate(8192);

// Write a btree at offset 123 in the area
long[] items = new long[400];
BTreeWriter writer = new BTreeWriter(array, ctx);
final int offsetInFile = 123;

long btreeSize = writer.write(offsetInFile, items.length, slice -> {
    // here we *must* write items.length * entry.size words in slice
    // these items must be sorted!!

    for (int i = 0; i < items.length; i++) {
        slice.set(i, items[i]);
    }
});

// Read the BTree

BTreeReader reader = new BTreeReader(array, ctx, offsetInFile);
reader.findEntry(items[0]);
```

## Useful Resources

Youtube: [Abdul Bari, 10.2 B Trees and B+ Trees. How they are useful in Databases](https://www.youtube.com/watch?v=aZjYr87r1b8). This isn't exactly the design implemented in this library, but very well presented and a good refresher.
