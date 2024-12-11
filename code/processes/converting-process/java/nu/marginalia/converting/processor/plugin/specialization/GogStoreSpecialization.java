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
public class GogStoreSpecialization extends DefaultSpecialization {

    @Inject
    public GogStoreSpecialization(SummaryExtractor summaryExtractor, TitleExtractor titleExtractor) {
        super(summaryExtractor, titleExtractor);
    }

    @Override
    public Document prune(Document original) {
        var pruned = super.prune(original);
        pruned.select(".age-gate").remove();
        return pruned;
    }


    @Override
    public String getSummary(Document original,
                             Set<String> importantWords) {
        var desc = original.select(".description").first();
        if (desc != null)
            return StringUtils.truncate(desc.text(), 255);
        return super.getSummary(original, importantWords);
    }

    public String getTitle(Document original, DocumentLanguageData dld, String url) {
        var appHubName = original.select(".productcard-basics__title").first();
        if (appHubName != null) {
            return StringUtils.truncate(appHubName.text(), 128);
        }

        return super.getTitle(original, dld, url);
    }

    @Override
    public boolean shouldIndex(EdgeUrl url) {
        return url.path.startsWith("/en/game/");
    }

}
