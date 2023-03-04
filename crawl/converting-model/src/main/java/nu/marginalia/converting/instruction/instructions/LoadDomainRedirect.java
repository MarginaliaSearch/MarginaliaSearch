package nu.marginalia.converting.instruction.instructions;

import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.InstructionTag;
import nu.marginalia.converting.instruction.Interpreter;

public record LoadDomainRedirect(DomainLink links) implements Instruction {

    @Override
    public void apply(Interpreter interpreter) {
        interpreter.loadDomainRedirect(links);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()+"["+ links+"]";
    }

    @Override
    public InstructionTag tag() {
        return InstructionTag.REDIRECT;
    }

    @Override
    public boolean isNoOp() {
        return false;
    }

}
