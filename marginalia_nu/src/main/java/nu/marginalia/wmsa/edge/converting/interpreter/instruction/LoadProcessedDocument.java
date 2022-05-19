package nu.marginalia.wmsa.edge.converting.interpreter.instruction;

import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.interpreter.InstructionTag;
import nu.marginalia.wmsa.edge.converting.interpreter.Interpreter;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeHtmlStandard;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlState;


public record LoadProcessedDocument(EdgeUrl url,
                                    EdgeUrlState state,
                                    String title,
                                    String description,
                                    int htmlFeatures,
                                    EdgeHtmlStandard standard,
                                    int length,
                                    long hash,
                                    double quality) implements Instruction
{
    @Override
    public void apply(Interpreter interpreter) {
        interpreter.loadProcessedDocument(this);
    }

    @Override
    public InstructionTag tag() {
        return InstructionTag.PROC_DOCUMENT;
    }

    @Override
    public boolean isNoOp() {
        return false;
    }
}
