package nu.marginalia.index.reverse.construction.full;

import com.google.common.io.MoreFiles;
import nu.marginalia.array.LongArrayFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Paths for a full preindex without holding any open mappings. */
public record FullPreindexReference(Path directory, boolean compressed) {
    static FullPreindexReference create(Path workDir, boolean compressed) throws IOException {
        return new FullPreindexReference(Files.createTempDirectory(workDir, "preindex-"), compressed);
    }

    public Path wordsFile() {
        return directory.resolve("words.dat");
    }

    public Path countsFile() {
        return directory.resolve("counts.dat");
    }

    public Path documentsFile() {
        return directory.resolve(compressed ? "docs.dat.zst" : "docs.dat");
    }

    public Path positionsFile() {
        return directory.resolve("positions.dat");
    }

    long sizeBytes() {
        try {
            return Files.size(wordsFile()) + Files.size(countsFile())
                    + Files.size(documentsFile()) + Files.size(positionsFile());
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public FullPreindex open() throws IOException {
        var words = LongArrayFactory.mmapForReadingShared(wordsFile());
        try {
            var counts = LongArrayFactory.mmapForReadingShared(countsFile());
            try {
                var segments = new FullPreindexWordSegments(words, counts);
                var documents = compressed
                        ? CompressedFullPreindexDocuments.open(documentsFile())
                        : LongArrayFactory.mmapForReadingShared(documentsFile());
                return new FullPreindex(segments, documents, positionsFile());
            }
            catch (IOException | RuntimeException | Error e) {
                counts.close();
                throw e;
            }
        }
        catch (IOException | RuntimeException | Error e) {
            words.close();
            throw e;
        }
    }

    public void delete() throws IOException {
        if (!Files.notExists(directory)) {
            MoreFiles.deleteRecursively(directory);
        }
    }
}
