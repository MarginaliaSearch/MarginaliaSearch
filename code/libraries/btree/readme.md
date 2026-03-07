# BTree

This library provides static B-tree and B+-tree indices for sorted long keys
with optional associated values.  Both binary indices (sets, entrySize=1) and
key-value mappings (entrySize=2+) are supported.

Two implementations exist behind the common interfaces
[BTreeReaderIf](java/nu/marginalia/btree/BTreeReaderIf.java) and
[BTreeWriterIf](java/nu/marginalia/btree/BTreeWriterIf.java):

## Paged B+-tree (new, preferred)

Located in `nu.marginalia.btree.paged`.

A page-oriented B+-tree that writes a self-contained file with a header,
leaf pages and internal pages.  Data lives only in the leaves; internal
nodes store separator keys and child pointers.

Key properties:
- Self-describing file format with a magic number (`0x42545245`),
  page size, entry size and tree height in the header.
- Configurable page size (power of 2, minimum 512 bytes).
- Two reader strategies:
  - **direct** -- O_DIRECT reads with a user-space LRU page cache.
  - **buffered** -- buffered reads via the OS page cache using
    pread() with fadvise(RANDOM).
- Written with [PagedBTreeWriter](java/nu/marginalia/btree/paged/PagedBTreeWriter.java),
  read with [PagedBTreeReader](java/nu/marginalia/btree/paged/PagedBTreeReader.java).

### Demo

```java
Path file = Path.of("index.bt");

// Write
var writer = new PagedBTreeWriter(file, 4096, 2); // pageSize=4096, entrySize=2
writer.write(items.length, sink -> {
    for (int i = 0; i < items.length; i++) {
        sink.put(keys[i], values[i]); // must be in ascending key order
    }
});

// Read (buffered, uses OS page cache)
try (var reader = PagedBTreeReader.buffered(file)) {
    long value = reader.getValue(someKey); // -1 if not found
}

// Read (direct, O_DIRECT with LRU cache)
try (var reader = PagedBTreeReader.direct(file, 256)) { // 256 cached pages
    long value = reader.getValue(someKey);
}
```

## Legacy B-tree (deprecated)

Located in `nu.marginalia.btree.legacy`.

An implicit pointer-less B-tree stored inside a memory-mapped `LongArray`.
The tree layout is described by a `LegacyBTreeContext` which specifies
the number of layers, entry size and block size.  This implementation has
known correctness issues and poor performance characteristics, and is
being phased out in favor of the paged B+-tree above.

## Useful Resources

Youtube: [Abdul Bari, 10.2 B Trees and B+ Trees. How they are useful in Databases](https://www.youtube.com/watch?v=aZjYr87r1b8).
This is not exactly the design implemented in this library, but it is very
well presented and a good refresher.
