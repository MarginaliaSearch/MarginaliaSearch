package nu.marginalia.index.model;

import nu.marginalia.index.config.ForwardIndexParameters;

/** Packed as: htmlFeatures(bits 0-31) | pubDate(bits 32-47) | docSize(bits 48-63) */
public class FeaturesCodec {

    public static long encode(
            int docFeatures,
            int docSize,
            short pubDate)
    {
        return (docFeatures & 0xFFFF_FFFFL)
                | ((long)(pubDate & 0xFFFF) << 32L)
                | ((long)(Math.min(docSize, 0xFFFF) & 0xFFFF) << 48L);
    }

    public static int getPubDate(long encoded, ForwardIndexParameters.ForwardIndexVersion version) {
        if (version == ForwardIndexParameters.ForwardIndexVersion.VERSION_LEGACY)
            return 0;

        return (int) ((encoded >>> 32L) & 0xFFFFL);
    }

    public static int getDocumentSize(long encoded, ForwardIndexParameters.ForwardIndexVersion version) {
        if (version == ForwardIndexParameters.ForwardIndexVersion.VERSION_LEGACY)
            return (int) ((encoded >>> 32L) & 0xFFFF_FFFFL);
        else
            return (int) ((encoded >>> 48L) & 0xFFFFL);
    }

    public static int getHtmlFeatures(long encoded) {
        return (int) (encoded & 0xFFFF_FFFFL);
    }
}
