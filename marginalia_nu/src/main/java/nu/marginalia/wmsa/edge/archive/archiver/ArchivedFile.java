package nu.marginalia.wmsa.edge.archive.archiver;

import lombok.Data;


public record ArchivedFile(String filename,byte[] data ) {
}
