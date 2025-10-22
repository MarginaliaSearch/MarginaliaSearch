package nu.marginalia.index.reverse.query;

import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.skiplist.SkipListValueRanges;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

class ReverseIndexDocumentRangeFiltersTest {

    @Test
    public void retainNone() {
        LongQueryBuffer lqb = new LongQueryBuffer(new long[] { 1, 2, 3, 4, 5 }, 5);
        SkipListValueRanges ranges = new SkipListValueRanges(new long[] { }, new long[] { });

        var filter = new ReverseIndexRetainDocumentRangeFilter(ranges);
        filter.apply(lqb);
        Assertions.assertArrayEquals(new long[] {}, lqb.copyData());
    }

    @Test
    public void rejectNone() {
        LongQueryBuffer lqb = new LongQueryBuffer(new long[] { 1, 2, 3, 4, 5 }, 5);
        SkipListValueRanges ranges = new SkipListValueRanges(new long[] { }, new long[] { });

        var filter = new ReverseIndexRejectDocumentRangeFilter(ranges);
        filter.apply(lqb);
        Assertions.assertArrayEquals(new long[] { 1, 2, 3, 4, 5 }, lqb.copyData());
    }

    @Test
    public void retainStart() {
        LongQueryBuffer lqb = new LongQueryBuffer(new long[] { 1, 2, 3, 4, 5 }, 5);
        SkipListValueRanges ranges = new SkipListValueRanges(new long[] { 0 }, new long[] { 2 });

        var filter = new ReverseIndexRetainDocumentRangeFilter(ranges);
        filter.apply(lqb);
        Assertions.assertArrayEquals(new long[] { 1 }, lqb.copyData());
    }

    @Test
    public void rejectStart() {
        LongQueryBuffer lqb = new LongQueryBuffer(new long[] { 1, 2, 3, 4, 5 }, 5);
        SkipListValueRanges ranges = new SkipListValueRanges(new long[] { 0 }, new long[] { 2 });

        var filter = new ReverseIndexRejectDocumentRangeFilter(ranges);
        filter.apply(lqb);
        Assertions.assertArrayEquals(new long[] { 2, 3, 4, 5 }, lqb.copyData());
    }

    @Test
    public void retainEnd() {
        LongQueryBuffer lqb = new LongQueryBuffer(new long[] { 1, 2, 3, 4, 5 }, 5);
        SkipListValueRanges ranges = new SkipListValueRanges(new long[] { 5 }, new long[] { 6 });

        var filter = new ReverseIndexRetainDocumentRangeFilter(ranges);
        filter.apply(lqb);
        Assertions.assertArrayEquals(new long[] { 5 }, lqb.copyData());
    }

    @Test
    public void rejectEnd() {
        LongQueryBuffer lqb = new LongQueryBuffer(new long[] { 1, 2, 3, 4, 5 }, 5);
        SkipListValueRanges ranges = new SkipListValueRanges(new long[] { 5 }, new long[] { 6 });

        var filter = new ReverseIndexRejectDocumentRangeFilter(ranges);
        filter.apply(lqb);
        System.out.printf(Arrays.toString(lqb.copyData()));
        Assertions.assertArrayEquals(new long[] { 1, 2, 3, 4 }, lqb.copyData());
    }

    @Test
    public void retainAll() {
        LongQueryBuffer lqb = new LongQueryBuffer(new long[] { 1, 2, 3, 4, 5 }, 5);
        SkipListValueRanges ranges = new SkipListValueRanges(new long[] { 1 }, new long[] { 6 });

        var filter = new ReverseIndexRetainDocumentRangeFilter(ranges);
        filter.apply(lqb);
        Assertions.assertArrayEquals(new long[] { 1, 2, 3, 4, 5 }, lqb.copyData());
    }


    @Test
    public void rejectAll() {
        LongQueryBuffer lqb = new LongQueryBuffer(new long[] { 1, 2, 3, 4, 5 }, 5);
        SkipListValueRanges ranges = new SkipListValueRanges(new long[] { 1 }, new long[] { 6 });

        var filter = new ReverseIndexRejectDocumentRangeFilter(ranges);
        filter.apply(lqb);
        Assertions.assertArrayEquals(new long[] { }, lqb.copyData());
    }

    @Test
    public void retainMulti() {
        LongQueryBuffer lqb = new LongQueryBuffer(new long[] { 1, 2, 3, 4, 5 }, 5);
        SkipListValueRanges ranges = new SkipListValueRanges(new long[] { 1, 4 }, new long[] { 3, 6 });

        var filter = new ReverseIndexRetainDocumentRangeFilter(ranges);
        filter.apply(lqb);
        Assertions.assertArrayEquals(new long[] { 1, 2, 4, 5 }, lqb.copyData());
    }

    @Test
    public void rejectMulti() {
        LongQueryBuffer lqb = new LongQueryBuffer(new long[] { 1, 2, 3, 4, 5 }, 5);
        SkipListValueRanges ranges = new SkipListValueRanges(new long[] { 1, 4 }, new long[] { 3, 6 });

        var filter = new ReverseIndexRejectDocumentRangeFilter(ranges);
        filter.apply(lqb);
        Assertions.assertArrayEquals(new long[] { 3 }, lqb.copyData());
    }


    @Test
    public void retainAll_with_additional_range() {
        LongQueryBuffer lqb = new LongQueryBuffer(new long[] { 1, 2, 3, 4, 5 }, 5);
        SkipListValueRanges ranges = new SkipListValueRanges(new long[] { 1, 7 }, new long[] { 6, 10 });

        var filter = new ReverseIndexRetainDocumentRangeFilter(ranges);
        filter.apply(lqb);
        Assertions.assertArrayEquals(new long[] { 1, 2, 3, 4, 5 }, lqb.copyData());
    }

    @Test
    public void rejectAll_with_additional_range() {
        LongQueryBuffer lqb = new LongQueryBuffer(new long[] { 1, 2, 3, 4, 5 }, 5);
        SkipListValueRanges ranges = new SkipListValueRanges(new long[] { 1, 7 }, new long[] { 6, 10 });

        var filter = new ReverseIndexRejectDocumentRangeFilter(ranges);
        filter.apply(lqb);
        Assertions.assertArrayEquals(new long[] { }, lqb.copyData());
    }

    @Test
    public void retainOverlap() {
        LongQueryBuffer lqb = new LongQueryBuffer(new long[] { 1, 2, 3, 4, 5 }, 5);
        SkipListValueRanges ranges = new SkipListValueRanges(new long[] { -2 }, new long[] { 10 });

        var filter = new ReverseIndexRetainDocumentRangeFilter(ranges);
        filter.apply(lqb);
        Assertions.assertArrayEquals(new long[] { 1, 2, 3, 4, 5 }, lqb.copyData());
    }

    @Test
    public void rejectOverlap() {
        LongQueryBuffer lqb = new LongQueryBuffer(new long[] { 1, 2, 3, 4, 5 }, 5);
        SkipListValueRanges ranges = new SkipListValueRanges(new long[] { -2 }, new long[] { 10 });

        var filter = new ReverseIndexRejectDocumentRangeFilter(ranges);
        filter.apply(lqb);
        Assertions.assertArrayEquals(new long[] { }, lqb.copyData());
    }
}