package nu.marginalia.converting.instruction.instructions;

import nu.marginalia.model.crawl.DocumentKeywords;
import nu.marginalia.model.idx.EdgePageDocumentsMetadata;
import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.InstructionTag;
import nu.marginalia.converting.instruction.Interpreter;
import nu.marginalia.model.EdgeUrl;

public record LoadKeywords(EdgeUrl url, EdgePageDocumentsMetadata metadata, DocumentKeywords words) implements Instruction {

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
