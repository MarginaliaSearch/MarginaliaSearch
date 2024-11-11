package nu.marginalia.converting.processor.pubdate.heuristic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import nu.marginalia.converting.model.DocumentHeaders;
import nu.marginalia.converting.processor.pubdate.PubDateEffortLevel;
import nu.marginalia.converting.processor.pubdate.PubDateHeuristic;
import nu.marginalia.converting.processor.pubdate.PubDateParser;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.html.HtmlStandard;
import org.jsoup.nodes.Document;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PubDateHeuristicJSONLD implements PubDateHeuristic {

    @Override
    public Optional<PubDate> apply(PubDateEffortLevel effortLevel, DocumentHeaders headers, EdgeUrl url, Document document, HtmlStandard htmlStandard) {
        for (var tag : document.select("script[type=\"application/ld+json\"]")) {
            var maybeDate = parseLdJson(tag.data())
                    .flatMap(PubDateParser::attemptParseDate);

            if (maybeDate.isPresent()) {
                return maybeDate;
            }
        }

        return Optional.empty();
    }

    private static final Gson gson = new GsonBuilder().create();

    public Optional<String> parseLdJson(String content) {
        try {
            var model = gson.fromJson(content, JsonModel.class);
            if (model == null)
                return Optional.empty();

            return Optional.ofNullable(model.getDatePublished());

        }
        catch (JsonSyntaxException|NumberFormatException|NullPointerException ex) {
            return Optional.empty();
        }
    }

}

class JsonModel {
    public String getDatePublished() {
        if (datePublished != null)
            return datePublished;

        for (var item : Objects.requireNonNullElse(graph,
                Collections.<JsonModelGraphItem>emptyList()))
        {
            if (null == item || !item.isRelevant())
                continue;

            if (item.datePublished != null)
                return item.datePublished;
        }

        return datePublished;
    }

    String datePublished;

    @SerializedName("@graph")
    List<JsonModelGraphItem> graph;
}

class JsonModelGraphItem {
    @SerializedName("@type")
    public String type;

    public String datePublished;

    public boolean isRelevant() {
        return "NewsArticle".equalsIgnoreCase(type)
                || "Article".equalsIgnoreCase(type);
    }

    public String toString() {
        return "JsonModelGraphItem(type=" + this.type + ", datePublished=" + this.datePublished + ")";
    }
}

