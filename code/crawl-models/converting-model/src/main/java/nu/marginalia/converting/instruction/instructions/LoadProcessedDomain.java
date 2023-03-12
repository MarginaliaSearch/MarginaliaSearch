package nu.marginalia.converting.instruction.instructions;

import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.InstructionTag;
import nu.marginalia.converting.instruction.Interpreter;
import nu.marginalia.model.EdgeDomain;

public record LoadProcessedDomain(EdgeDomain domain, DomainIndexingState state, String ip) implements Instruction {

    @Override
    public void apply(Interpreter interpreter) {
        interpreter.loadProcessedDomain(domain, state, ip);
    }

    @Override
    public InstructionTag tag() {
        return InstructionTag.PROC_DOMAIN;
    }

    @Override
    public boolean isNoOp() {
        return false;
    }

}
