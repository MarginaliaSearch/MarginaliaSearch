package nu.marginalia.memex.gemini.gmi;

import lombok.Getter;
import nu.marginalia.memex.gemini.gmi.line.AbstractGemtextLine;
import nu.marginalia.memex.gemini.gmi.parser.GemtextParser;
import nu.marginalia.memex.gemini.gmi.renderer.GemtextRenderer;
import nu.marginalia.memex.memex.model.MemexNodeHeadingId;
import nu.marginalia.memex.memex.model.MemexNodeUrl;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Getter
public class Gemtext {
    private final AbstractGemtextLine[] lines;
    private final MemexNodeUrl url;

    public Gemtext(MemexNodeUrl url, String[] lines, MemexNodeHeadingId headingRoot) {
        this.lines = GemtextParser.parse(lines, headingRoot);
        this.url = url;
    }
    public Gemtext(MemexNodeUrl url, String[] lines) {
        this.lines = GemtextParser.parse(lines, new MemexNodeHeadingId(0));
        this.url = url;
    }

    public String render(GemtextRenderer renderer) {
        return Arrays.stream(lines).map(renderer::renderLine).collect(Collectors.joining());
    }

    public void render(GemtextRenderer renderer, Writer w) throws IOException {
        for (var line : lines) {
            w.write(renderer.renderLine(line));
            w.write('\n');
        }
    }

    public Stream<AbstractGemtextLine> stream() {
        return Arrays.stream(lines);
    }

    public AbstractGemtextLine get(int idx) {
        return lines[idx];
    }
    public int size() {
        return lines.length;
    }

}
