package nu.marginalia.linkdb.model;

import nu.marginalia.model.EdgeUrl;

import javax.annotation.Nullable;

public record UrlStatus(long id, EdgeUrl url, String status, @Nullable String description) {
}
