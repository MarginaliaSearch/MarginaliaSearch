package nu.marginalia.index.query.filter;

import nu.marginalia.array.buffer.LongQueryBuffer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QueryFilterStepIfTest {

    private LongQueryBuffer createBuffer(long... data) {
        return new LongQueryBuffer(data, data.length);
    }

    @Test
    public void testPassThrough() {
        var buffer = createBuffer(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        var filter = new QueryFilterLetThrough();
        filter.apply(buffer);
        assertArrayEquals(new long[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, buffer.copyData());
    }

    @Test
    public void testNoPass() {
        var buffer = createBuffer(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        var filter = new QueryFilterNoPass();
        filter.apply(buffer);
        assertArrayEquals(new long[]{}, buffer.copyData());
    }

    @Test
    public void testIncludePredicate() {
        var buffer = createBuffer(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        var filter = new QueryFilterStepFromPredicate(value -> value % 2 == 0);
        filter.apply(buffer);
        assertArrayEquals(new long[]{2, 4, 6, 8, 10}, buffer.copyData());
    }

    @Test
    public void testExcludePredicate() {
        var buffer = createBuffer(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        var filter = new QueryFilterStepExcludeFromPredicate(value -> value % 2 == 1);
        filter.apply(buffer);
        assertArrayEquals(new long[]{2, 4, 6, 8, 10}, buffer.copyData());
    }

    @Test
    public void testSuccessiveApplication() {
        var buffer = createBuffer(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        var filter1 = new QueryFilterStepFromPredicate(value -> value % 2 == 0);
        var filter2 = new QueryFilterStepExcludeFromPredicate(value -> value <= 6);
        filter1.apply(buffer);
        filter2.apply(buffer);
        assertArrayEquals(new long[]{8, 10}, buffer.copyData());
    }

    @Test
    public void testCombinedApplication() {
        var buffer = createBuffer(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        var filter1 = new QueryFilterStepFromPredicate(value -> value % 3 == 0);
        var filter2 = new QueryFilterStepFromPredicate(value -> value % 5 == 0);
        var filter = new QueryFilterAnyOf(List.of(filter1, filter2));
        filter.apply(buffer);
        assertArrayEquals(new long[]{3, 5, 6, 9, 10}, buffer.copyData());
    }
}