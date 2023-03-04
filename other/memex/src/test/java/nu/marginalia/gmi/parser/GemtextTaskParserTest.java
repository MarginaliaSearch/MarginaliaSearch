package nu.marginalia.gmi.parser;

import nu.marginalia.memex.gemini.gmi.parser.GemtextTaskParser;
import nu.marginalia.memex.memex.model.MemexNodeHeadingId;
import nu.marginalia.memex.memex.model.MemexNodeTaskId;
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