package nu.marginalia.converting.instruction.instructions;

import nu.marginalia.crawling.common.model.HtmlStandard;
import nu.marginalia.model.crawl.UrlIndexingState;
import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.InstructionTag;
import nu.marginalia.converting.instruction.Interpreter;
import nu.marginalia.model.EdgeUrl;
import org.jetbrains.annotations.Nullable;


public record LoadProcessedDocument(EdgeUrl url,
                                    UrlIndexingState state,
                                    String title,
                                    String description,
                                    int htmlFeatures,
                                    HtmlStandard standard,
                                    int length,
                                    long hash,
                                    double quality,
                                    @Nullable Integer pubYear) implements Instruction
{
    @Override
    public void apply(Interpreter interpreter) {
        interpreter.loadProcessedDocument(this);
    }

    @Override
    public InstructionTag tag() {
        return InstructionTag.PROC_DOCUMENT;
    }

    @Override
    public boolean isNoOp() {
        return false;
    }
}
