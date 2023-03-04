package nu.marginalia.converting.instruction.instructions;

import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.InstructionTag;
import nu.marginalia.converting.instruction.Interpreter;
import nu.marginalia.model.EdgeDomain;

import java.util.Arrays;

public record LoadDomain(EdgeDomain... domain) implements Instruction {

    @Override
    public void apply(Interpreter interpreter) {
        interpreter.loadDomain(domain);
    }

    @Override
    public boolean isNoOp() {
        return domain.length == 0;
    }

    @Override
    public InstructionTag tag() {
        return InstructionTag.DOMAIN;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()+"["+Arrays.toString(domain)+"]";
    }
}
