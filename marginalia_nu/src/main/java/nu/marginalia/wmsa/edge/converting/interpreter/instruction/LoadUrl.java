package nu.marginalia.wmsa.edge.converting.interpreter.instruction;

import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.interpreter.InstructionTag;
import nu.marginalia.wmsa.edge.converting.interpreter.Interpreter;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

import java.util.Arrays;

public record LoadUrl(EdgeUrl... url) implements Instruction {

    @Override
    public void apply(Interpreter interpreter) {
        interpreter.loadUrl(url);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()+"["+ Arrays.toString(url)+"]";
    }

    @Override
    public InstructionTag tag() {
        return InstructionTag.URL;
    }

    @Override
    public boolean isNoOp() {
        return url.length == 0;
    }
}
