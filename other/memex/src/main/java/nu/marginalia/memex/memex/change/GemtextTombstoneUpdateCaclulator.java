package nu.marginalia.memex.memex.change;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.memex.memex.model.MemexNodeHeadingId;
import nu.marginalia.memex.memex.model.MemexNodeUrl;

import com.google.inject.name.Named;

import java.util.Objects;

@Singleton
public class GemtextTombstoneUpdateCaclulator {
    private final String tombstonePath;
    private final String redirectsPath;

    @Inject
    public GemtextTombstoneUpdateCaclulator(@Named("tombestone-special-file") String tombstonePath,
                                            @Named("redirects-special-file") String redirectsPath) {

        this.tombstonePath = tombstonePath;
        this.redirectsPath = redirectsPath;
    }

    public boolean isTombstoneFile(MemexNodeUrl url) {
        return Objects.equals(url, new MemexNodeUrl(tombstonePath));
    }
    public boolean isRedirectFile(MemexNodeUrl url) {
        return Objects.equals(url, new MemexNodeUrl(redirectsPath));
    }

    public GemtextMutation addTombstone(MemexNodeUrl url, String message) {
        var tombstoneUrl = new MemexNodeUrl(tombstonePath);

        return new GemtextCreateOrMutate(tombstoneUrl, "# Tombstones",
                new GemtextAppend(tombstoneUrl, new MemexNodeHeadingId(0),
                        new String[] { String.format("=> %s\t%s", url, message)}));
    }

    public GemtextMutation addRedirect(MemexNodeUrl url, String message) {
        var redirectsUrl = new MemexNodeUrl(redirectsPath);

        return new GemtextCreateOrMutate(redirectsUrl, "# Redirects",
                new GemtextAppend(redirectsUrl, new MemexNodeHeadingId(0),
                        new String[] { String.format("=> %s\t%s", url, message)}));
    }

}
