package nu.marginalia.btree.paged;

/** Abstraction for reading pages from a B+-tree file. */
interface BTreePageSource extends AutoCloseable {
    BTreePage get(long address);
    void close();
}
