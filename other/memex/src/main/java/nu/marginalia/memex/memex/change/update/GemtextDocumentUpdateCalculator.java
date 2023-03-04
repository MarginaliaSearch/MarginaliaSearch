package nu.marginalia.memex.memex.change.update;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.memex.gemini.gmi.line.GemtextText;
import nu.marginalia.memex.memex.Memex;
import nu.marginalia.memex.gemini.gmi.GemtextDocument;
import nu.marginalia.memex.gemini.gmi.line.AbstractGemtextLine;
import nu.marginalia.memex.gemini.gmi.line.GemtextHeading;
import nu.marginalia.memex.gemini.gmi.renderer.GemtextRenderer;
import nu.marginalia.memex.gemini.gmi.renderer.GemtextRendererFactory;
import nu.marginalia.memex.memex.change.*;
import nu.marginalia.memex.memex.model.MemexNodeHeadingId;
import nu.marginalia.memex.memex.model.MemexNodeUrl;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Singleton
public class GemtextDocumentUpdateCalculator {
    private final GemtextRenderer rawRenderer = new GemtextRendererFactory().gemtextRendererAsIs();
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Memex memex;

    @Inject
    public GemtextDocumentUpdateCalculator(Memex memex) {
        this.memex = memex;
    }

    public List<GemtextMutation> calculateUpdates(GemtextDocument original,
                                                  MemexNodeHeadingId destId,
                                                  GemtextDocument newSection)
    {

        var rewrite = new GemtextTasksRewrite(memex, original, destId, newSection);
        var lines = newSection.getLines();

        for (int i = 0; i < lines.length; i = Math.max(i+1, rewrite.processLine(lines, i)));

        List<GemtextMutation> updates = new ArrayList<>();

        updates.addAll(createKeepUpdates(original, destId, rewrite));
        updates.addAll(createDoneUpdates(original, destId, rewrite));
        updates.addAll(createTodoUpdates(original, rewrite));

        return updates;
    }

    private Collection<GemtextMutation>  createTodoUpdates(GemtextDocument original, GemtextTasksRewrite rewrite) {
        if (!rewrite.getPushToTodo().isEmpty()) {
            var doneDoc = original.getUrl().sibling("todo.gmi");

            var update = createTodoAction(rewrite.getPushToTodo(), doneDoc);
            return List.of(update);
        }
        return Collections.emptyList();
    }

    private Collection<GemtextMutation>  createDoneUpdates(GemtextDocument original, MemexNodeHeadingId destId, GemtextTasksRewrite rewrite) {
        if (!rewrite.getPushToDone().isEmpty()) {

            var doneDocUrl = original.getUrl().sibling("done.gmi");
            final String doneHeadingName = rewrite.getTodaysDoneHeadingName();

            var newDestId =
                    Optional.ofNullable(memex.getDocument(doneDocUrl))
                        .flatMap(dest -> dest.getHeadingByName(MemexNodeHeadingId.ROOT, doneHeadingName));

            if (newDestId.isEmpty()) {
                rewrite.getPushToDone().addAll(0,
                        List.of(new GemtextText("", MemexNodeHeadingId.ROOT),
                                new GemtextHeading(new MemexNodeHeadingId(1,1), doneHeadingName, destId))
                        );
            }

            var update = createDoneAction(rewrite.getPushToDone(), doneDocUrl, newDestId.orElse(new MemexNodeHeadingId(1)));
            return List.of(update);
        }
        return Collections.emptyList();
    }

    private Collection<GemtextMutation> createKeepUpdates(GemtextDocument original, MemexNodeHeadingId destId, GemtextTasksRewrite rewrite) {
        if (!rewrite.getKeep().isEmpty()) {
            return List.of(new GemtextReplace(original.getUrl(), destId, rewrite.getKeep().stream().map(rawRenderer::renderLine).toArray(String[]::new)));
        }
        return Collections.emptyList();
    }


    @NotNull
    private GemtextCreateOrMutate createDoneAction(List<AbstractGemtextLine> pushToDone, MemexNodeUrl doneDoc, MemexNodeHeadingId newDestId) {
        return new GemtextCreateOrMutate(
                doneDoc, "%%% TASKS\n# Done",
                new GemtextPrepend(doneDoc, newDestId, pushToDone.stream().map(rawRenderer::renderLine).toArray(String[]::new))
        );
    }

    @NotNull
    private GemtextCreateOrMutate createTodoAction(List<AbstractGemtextLine> pushToTodo, MemexNodeUrl doneDoc) {
        return new GemtextCreateOrMutate(
                doneDoc, "%%% TASKS\n# Todo",
                new GemtextAppend(doneDoc, new MemexNodeHeadingId(1), pushToTodo.stream().map(rawRenderer::renderLine).toArray(String[]::new))
        );
    }

}

