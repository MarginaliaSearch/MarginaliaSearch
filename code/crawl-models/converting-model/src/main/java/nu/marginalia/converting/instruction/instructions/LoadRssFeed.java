package nu.marginalia.converting.instruction.instructions;

import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.InstructionTag;
import nu.marginalia.converting.instruction.Interpreter;
import nu.marginalia.model.EdgeUrl;

import java.util.Arrays;

public record LoadRssFeed(EdgeUrl... feeds) implements Instruction {

    @Override
    public void apply(Interpreter interpreter) {
        interpreter.loadRssFeed(feeds);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()+"["+ Arrays.toString(feeds)+"]";
    }

    @Override
    public InstructionTag tag() {
        return InstructionTag.RSS;
    }

    @Override
    public boolean isNoOp() {
        return feeds.length == 0;
    }

}
