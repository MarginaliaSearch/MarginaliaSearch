package nu.marginalia.converting.instruction.instructions;

import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.InstructionTag;
import nu.marginalia.converting.instruction.Interpreter;
import nu.marginalia.model.EdgeUrl;

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
