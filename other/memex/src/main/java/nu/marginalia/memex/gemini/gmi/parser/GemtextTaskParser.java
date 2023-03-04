package nu.marginalia.memex.gemini.gmi.parser;

import nu.marginalia.memex.gemini.gmi.line.AbstractGemtextLine;
import nu.marginalia.memex.gemini.gmi.line.GemtextTask;
import nu.marginalia.memex.gemini.gmi.line.GemtextText;
import nu.marginalia.memex.memex.model.MemexNodeHeadingId;
import nu.marginalia.memex.memex.model.MemexNodeTaskId;
import nu.marginalia.memex.memex.model.MemexTaskTags;

import java.util.regex.Pattern;

public class GemtextTaskParser {
    private static final Pattern taskPattern = Pattern.compile("^(-+)\\s*([^-].*|$)$");

    public static AbstractGemtextLine parse(String s, MemexNodeHeadingId heading,
                                                                  MemexNodeTaskId taskId) {
        var matcher = taskPattern.matcher(s);

        if (!matcher.matches()) {
            return new GemtextText(s, heading);
        }

        int level = matcher.group(1).length() - 1;

        String task = matcher.group(2);

        return new GemtextTask(taskId.next(level), task, heading, new MemexTaskTags(task));
    }


}
