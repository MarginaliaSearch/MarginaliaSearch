package nu.marginalia.util.language;

import nu.marginalia.util.language.conf.LanguageModels;
import nu.marginalia.util.language.processing.KeywordCounter;
import nu.marginalia.util.language.processing.KeywordExtractor;
import nu.marginalia.util.language.processing.NameCounter;
import nu.marginalia.util.language.processing.SentenceExtractor;
import nu.marginalia.util.language.processing.model.DocumentSentence;
import nu.marginalia.util.language.processing.model.WordRep;
import nu.marginalia.util.language.processing.model.tag.WordSeparator;
import nu.marginalia.wmsa.edge.assistant.dict.TermFrequencyDict;
import org.jsoup.nodes.Document;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class DocumentDebugger {
    private final KeywordCounter kc;
    private final SentenceExtractor se;
    private final KeywordExtractor ke;
    private final NameCounter nc;

    final Map<String, Path> docsByPath = new TreeMap<>();
    Path tempDir;
    public DocumentDebugger(LanguageModels lm) throws IOException {
        se = new SentenceExtractor(lm);
        var dict = new TermFrequencyDict(lm);
        ke = new KeywordExtractor();

        kc = new KeywordCounter(dict, ke);
        nc = new NameCounter(ke);

        tempDir = Files.createTempDirectory("documentdebugger");
    }

    public void writeIndex() throws FileNotFoundException {
        var output = tempDir.resolve("index.html");

        try (var pw = new PrintWriter(new FileOutputStream(output.toFile()))) {
            pw.println("<ul>");

            docsByPath.forEach((name, path) -> {
                pw.println("<li>");
                pw.printf("<a href=\"file://%s\">%s</a>", path, name);
                pw.println("</li>");
            });


            pw.println("</ul>");
        }

        System.out.println(output);
    }

    public Path debugDocument(String name, Document document) throws IOException {

        var output = tempDir.resolve(name.substring(name.lastIndexOf("/")+1)+".html");
        docsByPath.put(name, output);

        document.select("table,sup,.reference").remove();
        var languageData = se.extractSentences(document);

        Set<String> reps = new HashSet<>();

//        kc.count(languageData, 0.75).forEach(rep -> reps.add(rep.stemmed));
//        kc.count(languageData).forEach(rep -> reps.add(rep.stemmed));

        try (var pw = new PrintWriter(new FileOutputStream(output.toFile()))) {

            for (var sent : languageData.titleSentences) {
                pw.print("<h1>");
                printSent(pw, sent, reps);
                pw.println("</h1>");
            }

            for (var sent : languageData.sentences) {
                pw.println("<div>");
                printSent(pw, sent, reps);
                pw.println("</div>");
            }
        }

        return output;
    }

    private void printSent(PrintWriter pw, DocumentSentence sent, Set<String> words) {
        TreeMap<Integer, Set<WordRep>> spans = new TreeMap<>();

        var names = ke.getKeywordsFromSentence(sent);

        for (var span : names) {
            for (int j = 0; j < span.size(); j++) {
                spans.computeIfAbsent(span.start + j, n -> new HashSet<>()).add(new WordRep(sent, span));
            }
        }

        for (int i = 0; i < sent.words.length; i++) {
            List<WordRep> matches = spans.getOrDefault(i, Collections.emptySet()).stream().filter(rep -> true || words.contains(rep.stemmed)).collect(Collectors.toList());

            printTag(pw, sent, i, matches);
        }
    }

    private void printTag(PrintWriter pw, DocumentSentence sent, int i, List<WordRep> matches) {

        String style;
        if (matches.isEmpty()) {
            style = "";
        }
        else if (matches.size() == 1 && !matches.get(0).word.contains("_")) {
            style = "text-decoration: underline; color: #00f";
        }
        else {
            style = "text-decoration: underline; color: #f00";
        }
        pw.printf("<ruby title=\"%s\" style=\"%s\">",
                matches.stream().map(rep -> rep.word).collect(Collectors.joining(", ")),
                style
                );
        pw.print(sent.words[i]);
        pw.print("<rt>"); pw.println(sent.posTags[i]); pw.print("</rt>");
        pw.print("</ruby> ");
        if (sent.separators[i] == WordSeparator.COMMA)
            pw.printf(", ");
    }
}
