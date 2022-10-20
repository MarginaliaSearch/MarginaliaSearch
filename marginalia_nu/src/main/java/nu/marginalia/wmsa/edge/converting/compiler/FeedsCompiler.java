package nu.marginalia.wmsa.edge.converting.compiler;

import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadRssFeed;
import nu.marginalia.wmsa.edge.converting.model.ProcessedDocument;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

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
