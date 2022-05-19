package nu.marginalia.gemini.gmi;

import nu.marginalia.wmsa.memex.model.MemexNodeUrl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class GemtextDatabaseTest {

    @Test
    public void test() {
        var db = new GemtextDatabase(new MemexNodeUrl("/test.gmi"), new String[] {
                "=> / foo",
                "=> /x bar",
                "=> /y baz",
                "=> /z"
        });
        verifyResult("foo", db.getLinkData(new MemexNodeUrl("/")));
        verifyResult("bar", db.getLinkData(new MemexNodeUrl("/x")));
        verifyResult("baz", db.getLinkData(new MemexNodeUrl("/y")));
        verifyResult("", db.getLinkData(new MemexNodeUrl("/z")));
        Assertions.assertFalse(db.getLinkData(new MemexNodeUrl("/w")).isPresent());
    }

    void verifyResult(String expected, @SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<String> actual) {
        Assertions.assertTrue(actual.isPresent(), () -> "No value found, expected " + expected);
        Assertions.assertEquals(expected, actual.get());
    }
}