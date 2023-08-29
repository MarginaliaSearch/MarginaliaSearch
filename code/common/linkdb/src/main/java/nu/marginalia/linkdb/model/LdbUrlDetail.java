package nu.marginalia.linkdb.model;

import nu.marginalia.model.EdgeUrl;

public record LdbUrlDetail(long urlId,
                           EdgeUrl url,
                           String title,
                           String description,
                           double urlQuality,
                           String format,
                           int features,
                           Integer pubYear,
                           long dataHash,
                           int wordsTotal
                        )

{
}
