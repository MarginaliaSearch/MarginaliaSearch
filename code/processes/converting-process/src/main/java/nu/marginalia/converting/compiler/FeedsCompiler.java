package nu.marginalia.converting.compiler;

import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.instructions.LoadRssFeed;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.model.EdgeUrl;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class FeedsCompiler {

    public void compile(Consumer<Instruction> instructionConsumer, List<ProcessedDocument> documents) {

        EdgeUrl[] feeds = documents.stream().map(doc -> doc.details)
                .filter(Objects::nonNull)
                .flatMap(dets -> dets.feedLinks.stream())
                .distinct()
                .toArray(EdgeUrl[]::new);

        instructionConsumer.accept(new LoadRssFeed(feeds));
    }
}
