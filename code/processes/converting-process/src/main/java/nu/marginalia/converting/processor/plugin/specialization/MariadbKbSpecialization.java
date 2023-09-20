package nu.marginalia.converting.processor.plugin.specialization;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.keyword.model.DocumentKeywordsBuilder;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.summary.SummaryExtractor;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Singleton
public class MariadbKbSpecialization extends DefaultSpecialization {
    private static final Logger logger = LoggerFactory.getLogger(MariadbKbSpecialization.class);

    @Inject
    public MariadbKbSpecialization(SummaryExtractor summaryExtractor) {
        super(summaryExtractor);
    }

    @Override
    public Document prune(Document doc) {
        var newDoc = new Document(doc.baseUri());
        var bodyTag = newDoc.appendElement("body");

        var comments = doc.getElementById("comments");
        if (comments != null)
            comments.remove();

        var contentTag= doc.getElementById("content");
        if (contentTag != null)
            bodyTag.appendChild(newDoc.createElement("section").html(contentTag.html()));

        return newDoc;
    }

    @Override
    public void amendWords(Document doc, DocumentKeywordsBuilder words) {
        Set<String> toAdd = new HashSet<>();

        for (var elem : doc.getElementsByTag("strong")) {
            var text = elem.text();

            if (text.contains(":"))
                continue;
            if (text.contains("("))
                continue;

            String[] keywords = text.toLowerCase().split("\\s+");
            if (keywords.length > 4)
                continue;

            toAdd.addAll(List.of(keywords));
            for (int i = 1; i < keywords.length; i++) {
                toAdd.add(keywords[i-1] + "_" + keywords[i]);
            }
        }

        System.out.println("Generated keywords: " +  toAdd);
        words.setFlagOnMetadataForWords(WordFlags.Subjects, toAdd);
    }

}