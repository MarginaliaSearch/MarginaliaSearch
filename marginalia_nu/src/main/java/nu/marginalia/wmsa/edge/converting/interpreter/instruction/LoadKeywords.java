package nu.marginalia.wmsa.edge.converting.interpreter.instruction;

import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.interpreter.InstructionTag;
import nu.marginalia.wmsa.edge.converting.interpreter.Interpreter;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

import java.util.Arrays;

public record LoadKeywords(EdgeUrl url, DocumentKeywords... words) implements Instruction {

    @Override
    public void apply(Interpreter interpreter) {
        interpreter.loadKeywords(url, words);
    }

    @Override
    public boolean isNoOp() {
        return words.length == 0;
    }

    @Override
    public InstructionTag tag() {
        return InstructionTag.WORDS;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()+"["+ Arrays.toString(words)+"]";
    }

}
