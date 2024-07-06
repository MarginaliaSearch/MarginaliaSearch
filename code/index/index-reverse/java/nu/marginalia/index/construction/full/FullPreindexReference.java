package nu.marginalia.index.construction.full;

import nu.marginalia.array.LongArrayFactory;

import java.io.IOException;
import java.nio.file.Path;

/** This is a dehydrated version of a FullPreIndex, that only
 * keeps references to its location on disk but does not hold associated
 * memory maps.
 */
public record FullPreindexReference(
        Path wordsFile,
        Path countsFile,
        Path documentsFile
)
{
    public FullPreindexReference(FullPreindexWordSegments segments, FullPreindexDocuments documents) {
        this(segments.wordsFile, segments.countsFile, documents.file);
    }

    public FullPreindex open() throws IOException {
        return new FullPreindex(
            new FullPreindexWordSegments(
                    LongArrayFactory.mmapForModifyingShared(wordsFile),
                    LongArrayFactory.mmapForModifyingShared(countsFile),
                    wordsFile,
                    countsFile
            ),
            new FullPreindexDocuments(
                    LongArrayFactory.mmapForModifyingShared(documentsFile),
                    documentsFile
            )
        );
    }
}
