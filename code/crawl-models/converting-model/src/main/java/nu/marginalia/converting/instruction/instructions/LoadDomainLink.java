package nu.marginalia.converting.instruction.instructions;

import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.InstructionTag;
import nu.marginalia.converting.instruction.Interpreter;

import java.util.Arrays;

public record LoadDomainLink(DomainLink... links) implements Instruction {

    @Override
    public void apply(Interpreter interpreter) {
        interpreter.loadDomainLink(links);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()+"["+ Arrays.toString(links)+"]";
    }

    @Override
    public InstructionTag tag() {
        return InstructionTag.LINK;
    }

    @Override
    public boolean isNoOp() {
        return links.length == 0;
    }

}
