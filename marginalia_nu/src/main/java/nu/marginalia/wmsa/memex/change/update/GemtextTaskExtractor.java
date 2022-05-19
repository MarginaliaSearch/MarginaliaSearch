package nu.marginalia.wmsa.memex.change.update;

import nu.marginalia.gemini.gmi.line.AbstractGemtextLine;
import nu.marginalia.gemini.gmi.line.GemtextTask;

import java.util.List;

class GemtextTaskExtractor {

    public static int extractTask(List<AbstractGemtextLine> dest, AbstractGemtextLine[] lines, int i) {
        var taskId = ((GemtextTask) lines[i]).getId();

        int j;
        for (j = i; j < lines.length; j++) {
            var item = lines[j];
            if (item.mapTask(GemtextTask::getId).map(id -> id.isChildOf(taskId)).orElse(false)) {
                dest.add(item);
            }
            else if (!item.breaksTask()) {
                dest.add(item);
            }
            else {
                break;
            }
        }
        if (j < lines.length) {
            return Math.max(i+1, j-1);
        }
        return lines.length;
    }
}
