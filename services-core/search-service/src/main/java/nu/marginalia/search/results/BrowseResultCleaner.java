package nu.marginalia.search.results;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.browse.model.BrowseResult;
import nu.marginalia.screenshot.ScreenshotService;
import nu.marginalia.model.id.EdgeId;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

@Singleton
public class BrowseResultCleaner {
    private final ScreenshotService screenshotService;

    @Inject
    public BrowseResultCleaner(ScreenshotService screenshotService) {
        this.screenshotService = screenshotService;
    }

    public Predicate<BrowseResult> shouldRemoveResultPredicate() {
        Set<String> domainHashes = new HashSet<>(100);

        return (res) -> !screenshotService.hasScreenshot(new EdgeId<>(res.domainId()))
                     || !domainHashes.add(res.domainHash());
    }
}
