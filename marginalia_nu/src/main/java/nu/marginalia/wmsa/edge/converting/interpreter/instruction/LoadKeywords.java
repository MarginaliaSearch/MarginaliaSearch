package nu.marginalia.wmsa.edge.converting.interpreter.instruction;

import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.interpreter.InstructionTag;
import nu.marginalia.wmsa.edge.converting.interpreter.Interpreter;
import nu.marginalia.wmsa.edge.index.model.EdgePageDocumentsMetadata;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

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
