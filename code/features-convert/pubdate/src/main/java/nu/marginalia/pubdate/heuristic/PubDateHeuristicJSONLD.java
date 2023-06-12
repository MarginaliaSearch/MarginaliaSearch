package nu.marginalia.pubdate.heuristic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import lombok.ToString;
import nu.marginalia.converting.model.HtmlStandard;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.pubdate.PubDateHeuristic;
import nu.marginalia.pubdate.PubDateParser;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.pubdate.PubDateEffortLevel;
import org.jsoup.nodes.Document;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PubDateHeuristicJSONLD implements PubDateHeuristic {

    @Override
    public Optional<PubDate> apply(PubDateEffortLevel effortLevel, String headers, EdgeUrl url, Document document, HtmlStandard htmlStandard) {
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

@ToString
class JsonModelGraphItem {
    @SerializedName("@type")
    public String type;

    public String datePublished;

    public boolean isRelevant() {
        return "NewsArticle".equalsIgnoreCase(type)
                || "Article".equalsIgnoreCase(type);
    }
}

