package nu.marginalia.rwf;

import nu.marginalia.array.LongArray;
import nu.marginalia.array.LongArrayFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** A RandomFileAssembler is a way to write a large file out of order
 * in a way that is efficient for SSDs.
 */
public interface RandomFileAssembler extends AutoCloseable {

    void put(long address, long data) throws IOException;
    void write(Path file) throws IOException;
    void close() throws IOException;


    /** Select the appropriate RandomFileAssembler implementation based on
     * the system configuration.
     */
    static RandomFileAssembler create(Path workDir,
                                      long totalSize) throws IOException {
        // If the system is configured to conserve memory, we use temp files
        if (Boolean.getBoolean("system.conserve-memory")) {
            return ofTempFiles(workDir);
        }

        // If the file is small, we use straight mmap
        if (totalSize < 128_000_000) { // 128M longs = 1 GB
            return ofMmap(workDir, totalSize);
        }

        // If the file is large, we use an in-memory buffer to avoid disk thrashing
        return ofInMemoryAsssembly(totalSize);

    }


    /** Create a RandomFileAssembler that writes to a series of small files.
     *  This has negligible memory overhead, but is slower than in-memory
     *  or mmap for small files.
     */
    static RandomFileAssembler ofTempFiles(Path workDir) throws IOException {

        return new RandomFileAssembler() {
            private final RandomWriteFunnel funnel = new RandomWriteFunnel(workDir, 10_000_000);
            @Override
            public void put(long address, long data) throws IOException {
                funnel.put(address, data);
            }

            @Override
            public void write(Path file) throws IOException {
                try (var channel = Files.newByteChannel(file, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
                    funnel.write(channel);
                }
            }

            @Override
            public void close() throws IOException {
                funnel.close();
            }
        };
    }

    /** Create a RandomFileAssembler that writes to a LongArray in memory. */
    static RandomFileAssembler ofInMemoryAsssembly(long size) {
        return new RandomFileAssembler() {
            private final LongArray buffer = LongArrayFactory.onHeapConfined(size);

            @Override
            public void put(long address, long data) {
                buffer.set(address, data);
            }

            @Override
            public void write(Path file) throws IOException {
                buffer.write(file);
            }

            @Override
            public void close() {
                buffer.close();
            }
        };
    }

    /** Create a RandomFileAssembler that writes to a file using mmap.
     *  This is the fastest method for small files, but has a large memory
     *  overhead and is slow for large files, where the OS will start pushing
     *  changes to disk continuously.
     * */
    static RandomFileAssembler ofMmap(Path destDir, long size) throws IOException {
        return new RandomFileAssembler() {
            private final Path workFile = Files.createTempFile(destDir, "mmap", ".dat");
            private final LongArray buffer = LongArrayFactory.mmapForWritingConfined(workFile, size);

            @Override
            public void put(long address, long data) {
                buffer.set(address, data);
            }

            @Override
            public void write(Path dest) throws IOException {
                buffer.force();

                Files.move(workFile, dest,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            }

            @Override
            public void close() throws IOException {
                buffer.close();

                // Catch the case where e.g. write() fails with an exception and workFile doesn't get moved
                Files.deleteIfExists(workFile);
            }
        };
    }
}
