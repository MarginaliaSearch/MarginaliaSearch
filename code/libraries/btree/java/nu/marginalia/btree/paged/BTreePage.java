package nu.marginalia.btree.paged;

/** Minimal view of a page for B+-tree traversal. */
interface BTreePage extends AutoCloseable {
    int getInt(int offset);
    long getLong(int offset);
    long pageAddress();
    void close();
}
