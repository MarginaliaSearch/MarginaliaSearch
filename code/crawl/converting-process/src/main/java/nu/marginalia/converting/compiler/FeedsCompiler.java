package nu.marginalia.converting.compiler;

import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.instructions.LoadRssFeed;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.model.EdgeUrl;

import java.util.List;
import java.util.Objects;

public class FeedsCompiler {

    public void compile(List<Instruction> ret, List<ProcessedDocument> documents) {

        EdgeUrl[] feeds = documents.stream().map(doc -> doc.details)
                .filter(Objects::nonNull)
                .flatMap(dets -> dets.feedLinks.stream())
                .distinct()
                .toArray(EdgeUrl[]::new);

        ret.add(new LoadRssFeed(feeds));
    }
}
