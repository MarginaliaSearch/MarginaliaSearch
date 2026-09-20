package nu.marginalia.index.reverse.construction.full;

import nu.marginalia.array.LongArrayFactory;

import java.io.IOException;
import java.nio.file.Path;

/** This is a dehydrated page of a FullPreIndex, that only
 * keeps references to its location on disk but does not hold associated
 * memory maps.
 */
public record FullPreindexReference(
        Path wordsFile,
        Path countsFile,
        Path documentsFile,
        boolean compressed
)
{
    public FullPreindexReference(FullPreindexWordSegments segments, FullPreindexDocuments documents) {
        this(segments.wordsFile, segments.countsFile, documents.file, documents.isCompressed());
    }

    public FullPreindexReference(Path wordsFile, Path countsFile, Path documentsFile) {
        this(wordsFile, countsFile, documentsFile, false);
    }

    public FullPreindex open() throws IOException {
        return new FullPreindex(
            new FullPreindexWordSegments(
                    LongArrayFactory.mmapForReadingShared(wordsFile),
                    LongArrayFactory.mmapForReadingShared(countsFile),
                    wordsFile,
                    countsFile
            ),
            FullPreindexDocuments.open(documentsFile, compressed)
        );
    }
}
