package nu.marginalia.index.index;

import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class QueryBranchWalkerTest {
    @Test
    public void testNoOverlap() {
        var paths = QueryBranchWalker.create(
                new long[] { 1, 2 },
                List.of(set(1), set(2))
        );
        assertEquals(2, paths.size());
        assertEquals(Set.of(1L, 2L), paths.stream().map(path -> path.termId).collect(Collectors.toSet()));
    }

    @Test
    public void testCond() {
        var paths = QueryBranchWalker.create(
                new long[] { 1, 2, 3, 4 },
                List.of(set(1,2,3), set(1,4,3))
        );
        assertEquals(1, paths.size());
        assertEquals(Set.of(1L), paths.stream().map(path -> path.termId).collect(Collectors.toSet()));
        System.out.println(Arrays.toString(paths.getFirst().priorityOrder));
        assertArrayEquals(new long[] { 2, 3, 4 }, paths.getFirst().priorityOrder);

        var next = paths.getFirst().next();
        assertEquals(2, next.size());
        assertEquals(Set.of(2L, 3L), next.stream().map(path -> path.termId).collect(Collectors.toSet()));
        Map<Long, QueryBranchWalker> byId = next.stream().collect(Collectors.toMap(w -> w.termId, w->w));
        assertArrayEquals(new long[] { 3L }, byId.get(2L).priorityOrder );
        assertArrayEquals(new long[] { 4L }, byId.get(3L).priorityOrder );
    }

    @Test
    public void testNoOverlapFirst() {
        var paths = QueryBranchWalker.create(
                new long[] { 1, 2, 3 },
                List.of(set(1, 2), set(1, 3))
        );
        assertEquals(1, paths.size());
        assertArrayEquals(new long[] { 2, 3 }, paths.getFirst().priorityOrder);
        assertEquals(Set.of(1L), paths.stream().map(path -> path.termId).collect(Collectors.toSet()));
    }

    LongSet set(long... args) {
        return new LongArraySet(args);
    }
}