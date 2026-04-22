package nu.marginalia.index.reverse.construction.full;

import nu.marginalia.array.LongArrayFactory;
import nu.marginalia.index.reverse.construction.IndexMergeOrdering;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** This is a dehydrated page of a FullPreIndex, that only
 * keeps references to its location on disk but does not hold associated
 * memory maps.
 */
public record FullPreindexReference(
        Path wordsFile,
        Path countsFile,
        Path documentsFile
)
implements IndexMergeOrdering.Mergable
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

    @Override
    public long estimateSize() {
        try {
            return Files.size(documentsFile);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
