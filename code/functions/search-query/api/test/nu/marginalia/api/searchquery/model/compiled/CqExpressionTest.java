package nu.marginalia.api.searchquery.model.compiled;

import it.unimi.dsi.fastutil.ints.IntList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class CqExpressionTest {

    @Test
    public void testGetPaths__trivial() {

        Assertions.assertEquals(List.of(IntList.of(0)), CompiledQueryParser.parse("1").root.paths());
        Assertions.assertEquals(List.of(IntList.of(0, 1)), CompiledQueryParser.parse("1 2").root.paths());
        Assertions.assertEquals(List.of(IntList.of(0), IntList.of(1)), CompiledQueryParser.parse("1 | 2").root.paths());

    }

}