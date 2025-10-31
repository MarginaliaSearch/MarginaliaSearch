package nu.marginalia.api.searchquery.model.compiled.aggregate;

import org.junit.jupiter.api.Test;

import static nu.marginalia.api.searchquery.model.compiled.CompiledQueryParser.parse;
import static nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates.booleanAggregate;
import static nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates.intMaxMinAggregate;
import static org.junit.jupiter.api.Assertions.*;

class CompiledQueryAggregatesTest {

    @Test
    void booleanAggregates() {
        assertFalse(booleanAggregate(parse("false"), Boolean::parseBoolean));
        assertTrue(booleanAggregate(parse("true"), Boolean::parseBoolean));
        assertFalse(booleanAggregate(parse("false true"), Boolean::parseBoolean));
        assertTrue(booleanAggregate(parse("( true ) | ( true false )"), Boolean::parseBoolean));
        assertTrue(booleanAggregate(parse("( false ) | ( true )"), Boolean::parseBoolean));
        assertTrue(booleanAggregate(parse("( true false ) | ( true true )"), Boolean::parseBoolean));
        assertFalse(booleanAggregate(parse("( true false ) | ( true false )"), Boolean::parseBoolean));
    }

    @Test
    void intMaxMinAggregates() {
        assertEquals(5, intMaxMinAggregate(parse("5"), Integer::parseInt));
        assertEquals(3, intMaxMinAggregate(parse("5 3"), Integer::parseInt));
        assertEquals(6, intMaxMinAggregate(parse("5 3 | 6 7"), Integer::parseInt));
    }

}