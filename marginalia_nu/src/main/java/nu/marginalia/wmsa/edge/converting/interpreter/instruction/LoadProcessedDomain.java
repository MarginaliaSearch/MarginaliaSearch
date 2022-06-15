package nu.marginalia.wmsa.edge.converting.interpreter.instruction;

import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.interpreter.InstructionTag;
import nu.marginalia.wmsa.edge.converting.interpreter.Interpreter;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;

public record LoadProcessedDomain(EdgeDomain domain, EdgeDomainIndexingState state, String ip) implements Instruction {

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
