package nu.marginalia.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class LineUtilsTest {

    @Test
    void firstNLines() {
        String text = "a\nb\r\ncd\r\re\n\rffgg\n\n";
        List<String> expected = List.of("a", "b", "cd", "", "e", "ffgg", "");
        Assertions.assertEquals(expected, LineUtils.firstNLines(text, 10));
    }
}