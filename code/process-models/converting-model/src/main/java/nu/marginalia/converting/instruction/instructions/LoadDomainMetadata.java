package nu.marginalia.converting.instruction.instructions;

import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.InstructionTag;
import nu.marginalia.converting.instruction.Interpreter;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;

import java.util.Arrays;

public record LoadDomainMetadata(EdgeDomain domain, int knownUrls, int goodUrls, int visitedUrls) implements Instruction {

    @Override
    public void apply(Interpreter interpreter) {
        interpreter.loadDomainMetadata(domain, knownUrls, goodUrls, visitedUrls);
    }

    @Override
    public boolean isNoOp() {
        return false;
    }

    @Override
    public InstructionTag tag() {
        return InstructionTag.DOMAIN_METADATA;
    }

}
