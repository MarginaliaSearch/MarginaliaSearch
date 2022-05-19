package nu.marginalia.wmsa.edge.converting.interpreter.instruction;

import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.interpreter.InstructionTag;
import nu.marginalia.wmsa.edge.converting.interpreter.Interpreter;

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
