package nu.marginalia.converting.compiler;

import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.instructions.LoadKeywords;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocument;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocumentWithError;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.model.crawl.HtmlFeature;

import java.util.List;
import java.util.function.Consumer;

public class DocumentsCompiler {

    public void compileDocumentDetails(Consumer<Instruction> instructionConsumer,
                                       ProcessedDocument doc,
                                       int ordinal) {
        var details = doc.details;

        if (details != null) {
            instructionConsumer.accept(new LoadProcessedDocument(doc.url,
                    ordinal,
                    doc.state,
                    details.title,
                    details.description,
                    HtmlFeature.encode(details.features),
                    details.standard.name(),
                    details.length,
                    details.hashCode,
                    details.quality,
                    details.pubYear
            ));
        }
        else {
            instructionConsumer.accept(new LoadProcessedDocumentWithError(
                    doc.url,
                    doc.state,
                    doc.stateReason,
                    ordinal
            ));
        }
    }

    public void compileWords(Consumer<Instruction> instructionConsumer,
                             ProcessedDocument doc,
                             int ordinal) {
        var words = doc.words;

        if (words != null) {
            instructionConsumer.accept(new LoadKeywords(doc.url,
                    ordinal,
                    HtmlFeature.encode(doc.details.features),
                    doc.details.metadata,
                    words.build())
            );
        }
    }

}
