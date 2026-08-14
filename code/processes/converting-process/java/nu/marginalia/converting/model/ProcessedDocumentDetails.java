package nu.marginalia.converting.model;

import com.github.luben.zstd.Zstd;
import nu.marginalia.model.DocumentFormat;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.idx.DocumentMetadata;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

public class ProcessedDocumentDetails {
    public String title;

    /** A text reconstruction of the body of the document.  The n:th word of the text corresponds to term
     * position ordinal n in the index, as seen by the positions data. */
    @Nullable
    public byte[] documentTextZstd;

    private static final int TEXT_COMPRESSION_LEVEL = 9;

    public void setDocumentText(String documentText) {
        if (documentText.isBlank())
            return;

        this.documentTextZstd = Zstd.compress(documentText.getBytes(StandardCharsets.UTF_8), TEXT_COMPRESSION_LEVEL);
    }

    @Nullable
    public Integer pubYear;

    public int pubDate;

    public int length;
    public double quality;
    public long hashCode;

    public Set<HtmlFeature> features;
    public DocumentFormat format;

    public List<EdgeUrl> linksInternal;
    public List<EdgeUrl> linksExternal;

    public DocumentMetadata metadata;
    public GeneratorType generator;
    public String languageIsoCode;

    public String toString() {
        return "ProcessedDocumentDetails(title=" + this.title + ", pubYear=" + this.pubYear + ", length=" + this.length + ", quality=" + this.quality + ", hashCode=" + this.hashCode + ", features=" + this.features + ", standard=" + this.format + ", linksInternal=" + this.linksInternal + ", linksExternal=" + this.linksExternal + ", metadata=" + this.metadata + ", generator=" + this.generator + ")";
    }
}
