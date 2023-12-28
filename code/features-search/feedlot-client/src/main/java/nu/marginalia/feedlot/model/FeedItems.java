package nu.marginalia.feedlot.model;

import java.util.List;

public record FeedItems(String domain, String feedUrl, String updated, List<FeedItem> items) {
}
