package nu.marginalia.encyclopedia.model;

import nu.marginalia.encyclopedia.cleaner.model.ArticleData;
import nu.marginalia.encyclopedia.cleaner.model.ArticleParts;
import nu.marginalia.encyclopedia.store.ArticleCodec;

public record Article (
        String url,
        String title,
        String summary,
        ArticleParts parts,
        LinkList urls,
        LinkList disambigs)
{

    public ArticleData asData() {
        return new ArticleData(
                url(),
                title(),
                summary(),
                ArticleCodec.toCompressedJson(parts),
                ArticleCodec.toCompressedJson(urls),
                ArticleCodec.toCompressedJson(disambigs)
        );
    }

    /** Used by template */
    public String articleHtml() {
        if (parts == null) {
            return "";
        }

        return parts.articleHtml();
    }
}
