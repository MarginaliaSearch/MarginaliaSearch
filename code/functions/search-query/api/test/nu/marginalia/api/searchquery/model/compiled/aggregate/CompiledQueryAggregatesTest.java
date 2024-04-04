package nu.marginalia.api.searchquery.model.compiled.aggregate;

import static nu.marginalia.api.searchquery.model.compiled.CompiledQueryParser.parse;
import static nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates.*;
import org.junit.jupiter.api.Test;

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

    @Test
    void doubleSumAggregates() {
        assertEquals(5, (int) doubleSumAggregate(parse("5"), Double::parseDouble));
        assertEquals(8, (int) doubleSumAggregate(parse("5 3"), Double::parseDouble));
        assertEquals(13, (int) doubleSumAggregate(parse("1 ( 5 3 | 2 10 )"), Double::parseDouble));
    }
}