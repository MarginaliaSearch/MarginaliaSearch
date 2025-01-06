package nu.marginalia.search.results;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.browse.model.BrowseResult;
import nu.marginalia.screenshot.ScreenshotService;

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

    public Predicate<BrowseResult> shouldRemoveResultPredicateBr() {
        Set<String> domainHashes = new HashSet<>(100);

        return (res) -> !screenshotService.hasScreenshot(res.domainId())
                     || !domainHashes.add(res.domainHash());
    }
}
