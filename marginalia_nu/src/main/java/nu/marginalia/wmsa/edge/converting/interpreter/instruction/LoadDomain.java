package nu.marginalia.wmsa.edge.converting.interpreter.instruction;

import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.interpreter.InstructionTag;
import nu.marginalia.wmsa.edge.converting.interpreter.Interpreter;
import nu.marginalia.wmsa.edge.model.EdgeDomain;

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
