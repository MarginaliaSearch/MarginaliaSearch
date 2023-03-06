package nu.marginalia.converting.instruction.instructions;

import nu.marginalia.converting.model.DocumentKeywords;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.InstructionTag;
import nu.marginalia.converting.instruction.Interpreter;
import nu.marginalia.model.EdgeUrl;

public record LoadKeywords(EdgeUrl url, DocumentMetadata metadata, DocumentKeywords words) implements Instruction {

    @Override
    public void apply(Interpreter interpreter) {
        interpreter.loadKeywords(url, metadata, words);
    }

    @Override
    public boolean isNoOp() {
        return false;
    }

    @Override
    public InstructionTag tag() {
        return InstructionTag.WORDS;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()+"["+ words+"]";
    }

}
