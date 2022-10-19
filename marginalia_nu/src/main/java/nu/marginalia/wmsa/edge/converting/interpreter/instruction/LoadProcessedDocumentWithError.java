package nu.marginalia.wmsa.edge.converting.interpreter.instruction;

import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.interpreter.InstructionTag;
import nu.marginalia.wmsa.edge.converting.interpreter.Interpreter;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeUrlState;


public record LoadProcessedDocumentWithError(EdgeUrl url,
                                             EdgeUrlState state,
                                             String reason) implements Instruction
{
    @Override
    public void apply(Interpreter interpreter) {
        interpreter.loadProcessedDocumentWithError(this);
    }

    @Override
    public InstructionTag tag() {
        return InstructionTag.PROC_DOCUMENT_ERR;
    }

    @Override
    public boolean isNoOp() {
        return false;
    }
}
