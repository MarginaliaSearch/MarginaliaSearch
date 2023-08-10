package nu.marginalia.converting.compiler;

import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.instructions.LoadKeywords;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocument;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.model.crawl.HtmlFeature;

import java.util.List;
import java.util.function.Consumer;

public class DocumentsCompiler {

    public void compile(Consumer<Instruction> instructionConsumer, List<ProcessedDocument> documents) {

        for (var doc : documents) {
            compileDocumentDetails(instructionConsumer, doc);
        }

        for (var doc : documents) {
            compileWords(instructionConsumer, doc);
        }

    }

    public void compileDocumentDetails(Consumer<Instruction> instructionConsumer, ProcessedDocument doc) {
        var details = doc.details;

        if (details != null) {
            instructionConsumer.accept(new LoadProcessedDocument(doc.url, doc.state, details.title, details.description, HtmlFeature.encode(details.features), details.standard.name(), details.length, details.hashCode, details.quality, details.pubYear));
        }
    }

    public void compileWords(Consumer<Instruction> instructionConsumer, ProcessedDocument doc) {
        var words = doc.words;

        if (words != null) {
            instructionConsumer.accept(new LoadKeywords(doc.url, doc.details.metadata, words.build()));
        }
    }

}
