package nu.marginalia.wmsa.edge.converting.interpreter.instruction;

import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.interpreter.InstructionTag;
import nu.marginalia.wmsa.edge.converting.interpreter.Interpreter;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

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
