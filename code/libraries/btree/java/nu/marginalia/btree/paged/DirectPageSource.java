package nu.marginalia.btree.paged;

import nu.marginalia.array.pool.BufferPool;
import nu.marginalia.array.pool.MemoryPage;

import java.nio.file.Path;

/** Page source using O_DIRECT reads with a user-space LRU cache. */
class DirectPageSource implements BTreePageSource {
    private final BufferPool pool;

    DirectPageSource(Path filePath, int pageSizeBytes, int poolSize) {
        this.pool = new BufferPool(filePath, pageSizeBytes, poolSize);
    }

    @Override
    public BTreePage get(long address) {
        MemoryPage page = pool.get(address);
        return new MemoryPageAdapter(page);
    }

    @Override
    public void close() {
        pool.close();
    }

    private record MemoryPageAdapter(MemoryPage page) implements BTreePage {
        @Override
        public int getInt(int offset) {
            return page.getInt(offset);
        }

        @Override
        public long getLong(int offset) {
            return page.getLong(offset);
        }

        @Override
        public long pageAddress() {
            return page.pageAddress();
        }

        @Override
        public void close() {
            page.close();
        }
    }
}
