package nu.marginalia.index.journal.model;

/** The header of an index journal file.  This is the first 16 bytes of the file,
 * and is not compressed.
 *
 * @param fileSizeRecords the size of the file in number of records
 * @param reserved should be 0
 */
public record IndexJournalFileHeader(long fileSizeRecords, long reserved) {
}
