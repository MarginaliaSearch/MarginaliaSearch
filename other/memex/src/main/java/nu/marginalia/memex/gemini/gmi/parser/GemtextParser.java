package nu.marginalia.memex.gemini.gmi.parser;

import nu.marginalia.memex.gemini.gmi.line.*;
import nu.marginalia.memex.memex.model.MemexNodeHeadingId;
import nu.marginalia.memex.memex.model.MemexNodeTaskId;

import java.util.*;

public class GemtextParser {

    private static final String PREFORMAT_MARKER = "```";
    private static final String LITERAL_MARKER = "  ";
    private static final String LINK_MARKER = "=>";
    private static final String HEADING_MARKER = "#";
    private static final String LIST_MARKER = "*";
    private static final String QUOTE_MARKER = ">";
    private static final String ASIDE_MARKER = "(";
    private static final String TASK_MARKER = "-";
    private static final String PRAGMA_MARKER = "%%%";

    public static AbstractGemtextLine[] parse(String[] lines, MemexNodeHeadingId headingRoot) {
        List<AbstractGemtextLine> items = new ArrayList<>();
        MemexNodeHeadingId heading = headingRoot;
        MemexNodeTaskId task = new MemexNodeTaskId(0);

        Set<String> pragmas = new HashSet<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (line.startsWith(PREFORMAT_MARKER)) {
                i = getBlockQuote(items, lines, heading, i);
            }
            else if (line.startsWith(PRAGMA_MARKER)) {
                var pragma = GemtextPragmaParser.parse(line, heading);

                if (pragma instanceof GemtextPragma) {
                    GemtextPragma gtp = (GemtextPragma) pragma;
                    pragmas.add(gtp.getLine());
                }

                items.add(pragma);

            }
            else if (line.startsWith(LINK_MARKER)) {
                var link = GemtextLinkParser.parse(line, heading);
                items.add(link);
            }
            else if (line.startsWith(HEADING_MARKER)) {
                var tag = GemtextHeadingParser.parse(line, heading);

                heading = tag.mapHeading(GemtextHeading::getHeading).orElse(heading);

                items.add(tag);
            }
            else if (line.startsWith(LIST_MARKER)) {
                i = getList(items, lines, heading, i);
            }
            else if (line.startsWith(LITERAL_MARKER)) {
                i = getLitteral(items, lines, heading, i);
            }
            else if (pragmas.contains("TASKS")
                    && line.startsWith(TASK_MARKER))
            {
                var tag = GemtextTaskParser.parse(line, heading, task);

                task = tag.mapTask(GemtextTask::getId).orElse(task);

                items.add(tag);
            }
            else if (line.startsWith(QUOTE_MARKER)) {
                i = getQuote(items, lines, heading, i);
            }
            else if (line.startsWith(ASIDE_MARKER)) {
                var aside = GemtextAsideParser.parse(line, heading);
                items.add(Objects.requireNonNullElse(aside, new GemtextText(line, heading)));
            }
            else {
                items.add(new GemtextText(line, heading));
            }
        }
        return items.toArray(AbstractGemtextLine[]::new);
    }

    private static int getBlockQuote(List<AbstractGemtextLine> items, String[] lines, MemexNodeHeadingId heading, int i) {
        int j = i+1;
        List<String> quotedLines = new ArrayList<>();
        for (;j < lines.length; j++) {
            if (lines[j].startsWith(PREFORMAT_MARKER)) {
                break;
            }
            quotedLines.add(lines[j]);
        }
        items.add(new GemtextPreformat(quotedLines, heading));
        return j;
    }

    private static int getList(List<AbstractGemtextLine> items, String[] lines, MemexNodeHeadingId heading, int i) {
        int j = i;
        List<String> listLines = new ArrayList<>();
        for (;j < lines.length; j++) {
            if (!lines[j].startsWith(LIST_MARKER)) {
                break;
            }
            listLines.add(GemtextListParser.parse(lines[j]));
        }
        items.add(new GemtextList(listLines, heading));
        return j-1;
    }
    private static int getLitteral(List<AbstractGemtextLine> items, String[] lines, MemexNodeHeadingId heading, int i) {
        int j = i;
        List<String> listLines = new ArrayList<>();
        for (;j < lines.length; j++) {
            if (!lines[j].startsWith(LITERAL_MARKER)) {
                break;
            }
            listLines.add(lines[j]);
        }
        items.add(new GemtextTextLiteral(listLines, heading));
        return j-1;
    }

    private static int getQuote(List<AbstractGemtextLine> items, String[] lines, MemexNodeHeadingId heading, int i) {
        int j = i;
        List<String> listLines = new ArrayList<>();
        for (;j < lines.length; j++) {
            if (!lines[j].startsWith(QUOTE_MARKER)) {
                break;
            }
            listLines.add(GemtextQuoteParser.parse(lines[j]));
        }
        items.add(new GemtextQuote(listLines, heading));
        return j-1;
    }
}
