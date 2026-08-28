package nu.marginalia.api.searchquery.model.compiled.aggregate;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates.booleanAggregate;
import static nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates.intMaxMinAggregate;
import static org.junit.jupiter.api.Assertions.*;

class CompiledQueryAggregatesTest {

    private static final String[] BOOLEANS = { "true", "false" };
    private static final String[] NUMBERS = { "5", "3", "6", "7" };

    @Test
    void booleanAggregates() {
        assertFalse(booleanAggregate(query(BOOLEANS, path(1)), Boolean::parseBoolean));
        assertTrue(booleanAggregate(query(BOOLEANS, path(0)), Boolean::parseBoolean));
        assertFalse(booleanAggregate(query(BOOLEANS, path(0, 1)), Boolean::parseBoolean));
        assertTrue(booleanAggregate(query(BOOLEANS, path(0), path(0, 1)), Boolean::parseBoolean));
        assertTrue(booleanAggregate(query(BOOLEANS, path(1), path(0)), Boolean::parseBoolean));
        assertFalse(booleanAggregate(query(BOOLEANS, path(0, 1), path(0, 1)), Boolean::parseBoolean));
    }

    @Test
    void intMaxMinAggregates() {
        assertEquals(5, intMaxMinAggregate(query(NUMBERS, path(0)), Integer::parseInt));
        assertEquals(3, intMaxMinAggregate(query(NUMBERS, path(0, 1)), Integer::parseInt));
        assertEquals(6, intMaxMinAggregate(query(NUMBERS, path(0, 1), path(2, 3)), Integer::parseInt));
    }

    private static IntList path(int... termIndices) {
        return IntList.of(termIndices);
    }

    private static CompiledQuery<String> query(String[] terms, IntList... paths) {
        return new CompiledQuery<>(new ArrayList<>(List.of(paths)),
                IntStream.range(0, terms.length).toArray(),
                terms);
    }

}
