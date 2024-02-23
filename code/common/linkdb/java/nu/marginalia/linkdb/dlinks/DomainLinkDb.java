package nu.marginalia.linkdb.dlinks;

import gnu.trove.list.array.TIntArrayList;

import java.nio.file.Path;

/** A database of source-destination pairs of domain IDs.  The database is loaded into memory from
 * a source.  The database is then kept in memory, reloading it upon switchInput().
 */
public interface DomainLinkDb {
    /** Replace the current db file with the provided file.  The provided file will be deleted.
     * The in-memory database MAY be updated to reflect the change.
     * */
    void switchInput(Path filename) throws Exception;

    /** Find all destinations for the given source. */
    TIntArrayList findDestinations(int source);

    /** Count the number of destinations for the given source. */
    int countDestinations(int source);

    /** Find all sources for the given destination. */
    TIntArrayList findSources(int dest);


    /** Count the number of sources for the given destination. */
    int countSources(int source);

    /** Iterate over all source-destination pairs. */
    void forEach(SourceDestConsumer consumer);


    interface SourceDestConsumer {
        void accept(int source, int dest);
    }
}
