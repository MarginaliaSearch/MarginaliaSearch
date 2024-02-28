package nu.marginalia.index.construction;

import nu.marginalia.array.LongArrayFactory;

import java.io.IOException;
import java.nio.file.Path;

/** This is a dehydrated version of a ReversePreIndex, that only
 * keeps references to its location on disk but does not hold associated
 * memory maps.
 */
public record ReversePreindexReference(
        Path wordsFile,
        Path countsFile,
        Path documentsFile
)
{
    public ReversePreindexReference(ReversePreindexWordSegments segments, ReversePreindexDocuments documents) {
        this(segments.wordsFile, segments.countsFile, documents.file);
    }

    public ReversePreindex open() throws IOException {
        return new ReversePreindex(
            new ReversePreindexWordSegments(
                    LongArrayFactory.mmapForModifyingShared(wordsFile),
                    LongArrayFactory.mmapForModifyingShared(countsFile),
                    wordsFile,
                    countsFile
            ),
            new ReversePreindexDocuments(
                    LongArrayFactory.mmapForModifyingShared(documentsFile),
                    documentsFile
            )
        );
    }
}
