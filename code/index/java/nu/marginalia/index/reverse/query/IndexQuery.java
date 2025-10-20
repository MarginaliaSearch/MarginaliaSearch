package nu.marginalia.index.reverse.query;

import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.index.reverse.query.filter.QueryFilterStepIf;
import nu.marginalia.skiplist.SkipListReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** A query to the index.  The query is composed of a list of sources
 * and a list of filters.
 * <p></p>
 * The sources are read in order, and the filters are applied to the results.
 * <p></p>
 * The query is executed by providing it with a buffer to fill with results,
 * and
 */
public class IndexQuery {
    private final List<EntrySource> sources;
    private final List<QueryFilterStepIf> inclusionFilter = new ArrayList<>(10);
    private boolean prioritize = false;

    public IndexQuery(EntrySource source, boolean prioritize)
    {
        this.sources = List.of(source);
        this.prioritize = prioritize;
    }

    public boolean isPrioritized() {
        return prioritize;
    }
    /** Adds a filter to the query.  The filter will be applied to the results
     * after they are read from the sources.
     *
     * @param filter  The filter to add
     */
    public void addInclusionFilter(QueryFilterStepIf filter) {
        inclusionFilter.add(filter);
    }

    private int si = 0;
    private int dataCost;

    /** Returns true if there are more results to read from the sources.
     *  May return true even if there are no more results, but will eventually
     *  return false.
     */
    public boolean hasMore() {
        return si < sources.size();
    }

    public boolean isNoOp() {
        for (var source : sources) {
            if (!(source instanceof EmptyEntrySource))
                return false;
        }
        return true;
    }

    /** Fills the given buffer with more results from the sources.
     *  The results are filtered by the inclusion filters.
     *  <p></p>
     *  The method will advance the sources and filters as needed
     *  to fill the buffer.
     *
     * @param dest  The buffer to fill with results
     */
    public void getMoreResults(LongQueryBuffer dest) {
        if (!fillBuffer(dest))
            return;


        for (var filter : inclusionFilter) {
            filter.apply(dest);

            dataCost += dest.size();

            if (dest.isEmpty()) {
                return;
            }
        }
    }

    private boolean fillBuffer(LongQueryBuffer dest) {
        for (;;) {
            dest.zero();

            EntrySource source = sources.get(si);
            source.read(dest);

            if (!dest.isEmpty()) {
                break;
            }

            if (!source.hasMore() && ++si >= sources.size())
                return false;
        }

        dataCost += dest.size();

        return !dest.isEmpty();
    }

    public void printDebugInformation() {
        System.out.println("Debug information for query: ");

        for (var source: sources) {
            System.out.println(source.indexName() + ": " + source.readEntries());
        }

        for (var step : inclusionFilter) {

            if (step instanceof ReverseIndexRetainFilter(SkipListReader range, String name, String term, _)) {
                Map<Integer, Integer> histoMap = new TreeMap<>();

                for (int i = 0; i < range.__stats_match_histo_retain.length; i++) {
                    int val = range.__stats_match_histo_retain[i];
                    if (val != 0) histoMap.put(i, val);
                }

                System.out.println("Retain " + name + " " + term + ": " + histoMap.toString());
            }
            else if (step instanceof ReverseIndexRejectFilter(SkipListReader range, String term, _)) {
                Map<Integer, Integer> histoMap = new TreeMap<>();

                for (int i = 0; i < range.__stats_match_histo_reject.length; i++) {
                    int val = range.__stats_match_histo_reject[i];
                    if (val != 0) histoMap.put(i, val);
                }

                System.out.println("Retain " + term + ": " + histoMap.toString());
            }
        }
    }

    public long dataCost() {
        return dataCost;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(sources.stream().map(EntrySource::indexName).collect(Collectors.joining(", ", "[", "]")));
        sb.append(" -> ");
        sb.append(inclusionFilter.stream().map(QueryFilterStepIf::describe).collect(Collectors.joining(", ", "[", "]")));

        return sb.toString();
    }

}


