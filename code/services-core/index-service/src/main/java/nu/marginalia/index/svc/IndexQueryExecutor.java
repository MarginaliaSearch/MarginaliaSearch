package nu.marginalia.index.svc;

import com.google.inject.Singleton;
import gnu.trove.list.array.TLongArrayList;
import nu.marginalia.array.buffer.LongQueryBuffer;
import nu.marginalia.index.query.IndexQuery;

@Singleton
public class IndexQueryExecutor {

    /* Re-use these buffers as they contribute to a large amount of memory churn */
    private static final ThreadLocal<LongQueryBuffer> bufferTL = ThreadLocal.withInitial(() -> new LongQueryBuffer(4096));

    public int executeQuery(IndexQuery query, TLongArrayList results, SearchParameters params)
    {
        final int fetchSize = params.fetchSize * query.fetchSizeMultiplier;

        final LongQueryBuffer buffer = bufferTL.get();

        int cnt = 0;

        while (query.hasMore()
                && results.size() < fetchSize
                && params.budget.hasTimeLeft())
        {
            buffer.reset();
            query.getMoreResults(buffer);

            for (int i = 0; i < buffer.size() && results.size() < fetchSize; i++) {
                results.add(buffer.data[i]);
                cnt++;
            }
        }

        params.dataCost += query.dataCost();

        return cnt;
    }

}
