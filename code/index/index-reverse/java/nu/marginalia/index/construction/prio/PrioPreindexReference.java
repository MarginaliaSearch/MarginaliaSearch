package nu.marginalia.index.construction.prio;

import nu.marginalia.array.LongArrayFactory;

import java.io.IOException;
import java.nio.file.Path;

/** This is a dehydrated page of a PrioPreIndex, that only
 * keeps references to its location on disk but does not hold associated
 * memory maps.
 */
public record PrioPreindexReference(
        Path wordsFile,
        Path countsFile,
        Path documentsFile
)
{
    public PrioPreindexReference(PrioPreindexWordSegments segments, PrioPreindexDocuments documents) {
        this(segments.wordsFile, segments.countsFile, documents.file);
    }

    public PrioPreindex open() throws IOException {
        return new PrioPreindex(
            new PrioPreindexWordSegments(
                    LongArrayFactory.mmapForModifyingShared(wordsFile),
                    LongArrayFactory.mmapForModifyingShared(countsFile),
                    wordsFile,
                    countsFile
            ),
            new PrioPreindexDocuments(
                    LongArrayFactory.mmapForModifyingShared(documentsFile),
                    documentsFile
            )
        );
    }
}
