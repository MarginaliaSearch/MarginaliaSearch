package nu.marginalia.converting.processor.plugin.specialization;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.converting.processor.logic.TitleExtractor;
import nu.marginalia.converting.processor.summary.SummaryExtractor;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.model.EdgeUrl;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;

import java.util.Set;

@Singleton
public class SteamStoreSpecialization extends DefaultSpecialization {

    @Inject
    public SteamStoreSpecialization(SummaryExtractor summaryExtractor, TitleExtractor titleExtractor) {
        super(summaryExtractor, titleExtractor);
    }

    @Override
    public Document prune(Document original) {
        var glanceCtn = original.select(".glance_ctn").clone().first();
        var gameAreaDesc= original.select("#game_area_description").clone().first();
        var appHubName = original.select("#appHubAppName").clone().first();

        var newDoc = new Document(original.baseUri());
        var title = newDoc.head().appendElement("title");
        var bodyTag = newDoc.appendElement("body");
        if (appHubName != null) {
            title.appendText(appHubName.text());
            bodyTag.appendChild(appHubName);
        }
        if (glanceCtn != null) {
            bodyTag.appendChild(glanceCtn);
        }
        if (gameAreaDesc != null) {
            bodyTag.appendChild(gameAreaDesc);
        }

        return newDoc;
    }

    public String getTitle(Document original, DocumentLanguageData dld, String url) {
        var appHubName = original.select("#appHubAppName").first();
        if (appHubName != null) {
            return StringUtils.truncate(appHubName.text(), 128);
        }

        return super.getTitle(original, dld, url);
    }

    @Override
    public String getSummary(Document original, Set<String> importantWords) {
        // Trust wikis to generate a useful summary
        var gameDesc = original.select(".game_description_snippet");
        gameDesc = gameDesc.clone();

        gameDesc.select("h2").remove();
        String desc = gameDesc.text();
        if (!desc.isBlank()) {
            return StringUtils.truncate(desc, 255);
        }
        else {
            return super.getSummary(original, importantWords);
        }
    }

    @Override
    public boolean shouldIndex(EdgeUrl url) {
        return url.path.startsWith("/app/");
    }

}
