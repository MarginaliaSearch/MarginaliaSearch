package nu.marginalia.gemini.gmi.parser;

import nu.marginalia.wmsa.memex.model.MemexNodeHeadingId;
import nu.marginalia.wmsa.memex.model.MemexNodeTaskId;
import org.junit.jupiter.api.Test;

class GemtextTaskParserTest {

    @Test
    void parse() {
        System.out.println(GemtextTaskParser.parse("-task", new MemexNodeHeadingId(0), new MemexNodeTaskId(0)));
        System.out.println(GemtextTaskParser.parse("- task", new MemexNodeHeadingId(0), new MemexNodeTaskId(0)));
        System.out.println(GemtextTaskParser.parse("--task", new MemexNodeHeadingId(0), new MemexNodeTaskId(0)));
        System.out.println(GemtextTaskParser.parse("-task(/)", new MemexNodeHeadingId(0), new MemexNodeTaskId(0)));
        System.out.println(GemtextTaskParser.parse("-task(-)", new MemexNodeHeadingId(0), new MemexNodeTaskId(0)));
        System.out.println(GemtextTaskParser.parse("-task(?)(x)", new MemexNodeHeadingId(0), new MemexNodeTaskId(0)));
    }
}