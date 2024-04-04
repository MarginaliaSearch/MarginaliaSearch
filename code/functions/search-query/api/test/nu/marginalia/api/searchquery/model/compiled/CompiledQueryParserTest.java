package nu.marginalia.api.searchquery.model.compiled;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompiledQueryParserTest {

    @Test
    public void testEmpty() {
        assertEquals(CqExpression.empty(), CompiledQueryParser.parse("").root);
        assertEquals(CqExpression.empty(), CompiledQueryParser.parse("( )").root);
        assertEquals(CqExpression.empty(), CompiledQueryParser.parse("( | )").root);
        assertEquals(CqExpression.empty(), CompiledQueryParser.parse("| ( | ) |").root);
    }

    @Test
    public void testSingleWord() {
        CompiledQuery<String> q = CompiledQueryParser.parse("foo");
        assertEquals(w(q, "foo"), q.root);
    }

    @Test
    public void testAndTwoWords() {
        CompiledQuery<String> q = CompiledQueryParser.parse("foo bar");
        assertEquals(and(w(q, "foo"), w(q,"bar")), q.root);
    }

    @Test
    public void testOrTwoWords() {
        CompiledQuery<String> q = CompiledQueryParser.parse("foo | bar");
        assertEquals(or(w(q, "foo"), w(q,"bar")), q.root);
    }

    @Test
    public void testOrAndWords() {
        CompiledQuery<String> q = CompiledQueryParser.parse("foo | bar baz");
        assertEquals(or(w(q,"foo"), and(w(q,"bar"), w(q,"baz"))), q.root);
    }

    @Test
    public void testAndAndOrAndAndWords() {
        CompiledQuery<String> q = CompiledQueryParser.parse("foo foobar | bar baz");
        assertEquals(or(
                and(w(q, "foo"), w(q, "foobar")),
                and(w(q, "bar"), w(q, "baz")))
                , q.root);
    }
    @Test
    public void testComplex1() {
        CompiledQuery<String> q = CompiledQueryParser.parse("foo ( bar | baz ) quux");
        assertEquals(and(w(q,"foo"), or(w(q, "bar"), w(q, "baz")), w(q, "quux")), q.root);
    }
    @Test
    public void testComplex2() {
        CompiledQuery<String> q = CompiledQueryParser.parse("( ( ( a ) b ) c ) d");
        assertEquals(and(and(and(w(q, "a"), w(q, "b")), w(q, "c")),  w(q, "d")), q.root);
    }

    @Test
    public void testNested() {
        CompiledQuery<String> q = CompiledQueryParser.parse("( ( ( a ) ) )");
        assertEquals(w(q,"a"), q.root);
    }

    private CqExpression.Word w(CompiledQuery<String> query, String word) {
        return new CqExpression.Word(query.indices().filter(idx -> word.equals(query.at(idx))).findAny().orElseThrow());
    }

    private CqExpression and(CqExpression... parts) {
        return new CqExpression.And(List.of(parts));
    }

    private CqExpression or(CqExpression... parts) {
        return new CqExpression.Or(List.of(parts));
    }
}