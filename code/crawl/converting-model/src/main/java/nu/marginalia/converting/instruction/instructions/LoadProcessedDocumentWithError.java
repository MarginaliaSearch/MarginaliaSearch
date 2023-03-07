package nu.marginalia.converting.instruction.instructions;

import nu.marginalia.model.crawl.EdgeUrlState;
import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.InstructionTag;
import nu.marginalia.converting.instruction.Interpreter;
import nu.marginalia.model.EdgeUrl;


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
