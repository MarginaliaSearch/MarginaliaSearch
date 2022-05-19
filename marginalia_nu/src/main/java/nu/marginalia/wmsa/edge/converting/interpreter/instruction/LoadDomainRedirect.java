package nu.marginalia.wmsa.edge.converting.interpreter.instruction;

import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.interpreter.InstructionTag;
import nu.marginalia.wmsa.edge.converting.interpreter.Interpreter;

import java.util.Arrays;

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
