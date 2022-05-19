package nu.marginalia.wmsa.memex.change.update;

import lombok.Getter;
import nu.marginalia.gemini.gmi.GemtextDocument;
import nu.marginalia.gemini.gmi.line.AbstractGemtextLine;
import nu.marginalia.gemini.gmi.line.GemtextTask;
import nu.marginalia.wmsa.memex.Memex;
import nu.marginalia.wmsa.memex.model.MemexNodeHeadingId;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Getter
class GemtextTasksRewrite {
    private final List<AbstractGemtextLine> keep = new ArrayList<>();
    private final List<AbstractGemtextLine> pushToTodo = new ArrayList<>();
    private final List<AbstractGemtextLine> pushToDone = new ArrayList<>();

    private final String rootHeadingName = "Done";
    private final String todoHeadingName = "Todo";
    private final String backlogHeadingName = "Backlog";

    private final boolean isDestTodo;
    private final boolean isDestDone;
    private final Memex memex;
    private final GemtextDocument original;
    private final MemexNodeHeadingId destId;
    private final GemtextDocument newSection;

    GemtextTasksRewrite(Memex memex, GemtextDocument original, MemexNodeHeadingId destId, GemtextDocument newSection) {
        this.memex = memex;
        this.original = original;
        this.destId = destId;
        this.newSection = newSection;


        isDestTodo = isDestTodo(original, destId);
        isDestDone = isDestDone(original, destId);
    }

    public int processLine(AbstractGemtextLine[] lines, int i) {
        var line = lines[i];

        if (!line.mapTask(GemtextTask::getLevel).map(level -> 1 == level).orElse(false)) {
            keep.add(line);
            return i + 1;
        }

        // It's a task

        boolean isTaskDone = line.mapTask(GemtextTask::getState).map(state -> state.done).orElse(false);
        boolean isChangeDestDone = matchHeadingHierarchy(newSection, line.getHeading(), heading -> heading.contains(rootHeadingName));
        boolean isChangeDestTodo = isDestTodo(newSection, line.getHeading());

        if (isTaskDone && !isDestDone && !isChangeDestDone) {
            return GemtextTaskExtractor.extractTask(pushToDone, lines, i);
        } else if (!isTaskDone && !isDestTodo && !isChangeDestTodo) {
            return GemtextTaskExtractor.extractTask(pushToTodo, lines, i);
        }

        keep.add(line);
        return i + 1;
    }

    private boolean isDestDone(GemtextDocument original, MemexNodeHeadingId destId) {

        final String currentHeadingName = getTodaysDoneHeadingName();

        return matchHeadingHierarchy(original, destId, heading -> heading.contains(currentHeadingName))
                || matchHeadingHierarchy(original, destId, heading -> heading.contains(rootHeadingName));
    }

    @NotNull
    public String getTodaysDoneHeadingName() {
        return "Done " + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private boolean isDestTodo(GemtextDocument original, MemexNodeHeadingId destId) {
        return matchHeadingHierarchy(original, destId, heading -> heading.contains(todoHeadingName))
                || matchHeadingHierarchy(original, destId, heading -> heading.contains(backlogHeadingName));
    }


    boolean matchHeadingHierarchy(GemtextDocument doc, MemexNodeHeadingId heading, Predicate<String> p) {

        for (; !heading.equals(MemexNodeHeadingId.ROOT); heading = heading.parent()) {
            var maybeTitle = doc.getHeading(heading);
            if (maybeTitle.map(p::test).orElse(false)) {
                return true;
            }

        }
        return false;
    }

}
