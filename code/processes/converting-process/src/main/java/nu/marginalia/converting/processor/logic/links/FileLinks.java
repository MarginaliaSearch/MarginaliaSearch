package nu.marginalia.converting.processor.logic.links;

import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import org.jsoup.nodes.Document;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class FileLinks {

    // If a document links to a file on the same server, and that file has
    // an appropriate file ending, then add the filename as a keyword so that it can
    // be found even if it's not explicitly mentioned on the page
    public static Set<String> createFileLinkKeywords(LinkProcessor lp, EdgeDomain domain) {
        Set<String> fileKeywords = new HashSet<>(100);

        for (var link : lp.getNonIndexableUrls()) {

            if (!domain.hasSameTopDomain(link.domain)) {
                continue;
            }

            synthesizeFilenameKeyword(fileKeywords, link);

        }

        return fileKeywords;
    }

    private static void synthesizeFilenameKeyword(Set<String> fileKeywords, EdgeUrl link) {

        Path pFilename = Path.of(link.path.toLowerCase()).getFileName();

        if (pFilename == null) return;

        String filename = pFilename.toString();
        if (filename.length() > 32
                || filename.endsWith(".xml")
                || filename.endsWith(".jpg")
                || filename.endsWith(".png")
                || filename.endsWith(".pdf")
                || filename.endsWith(".gif"))
            return;

        fileKeywords.add(filename.replace(' ', '_'));
    }

    /** Create synthetic keywords for file endings of files linked within the same server.
     * Also generate categorical keywords for the type of file (audio, video, image, document, archive)
     */
    public static Set<String> createFileEndingKeywords(Document doc) {
        Set<String> endings = new HashSet<>();

        doc.getElementsByTag("a").forEach(e -> {
            var src = e.attr("href");

            if (src.contains(":")) return;

            if (src.contains("/")) src = src.substring(src.lastIndexOf("/"));
            if (src.contains("?")) src = src.split("\\?", 2)[0];
            if (src.contains("#")) src = src.split("#", 2)[0];

            src = src.toLowerCase();

            if (src.startsWith("www")) return;

            final int firstPeriod = src.indexOf(".");
            final int lastPeriod = src.lastIndexOf(".");

            if (firstPeriod < 0) return;
            if (firstPeriod != lastPeriod) return;

            String ending = src.substring(lastPeriod + 1).trim();

            if (ending.contains("_")) return;
            if (ignoredEndings.contains(ending)) return;

            int endingLength = ending.length();
            if (endingLength > 1 && endingLength <= 4) {
                endings.add(ending);
            }
        });

        if (endings.isEmpty())
            return endings;

        Set<String> keywords = new HashSet<>(endings.size() + 8);
        for (var ending : endings) {
            keywords.add("file:" + ending);
        }

        if (hasEndingType(audioEndings, endings)) keywords.add("file:audio");
        if (hasEndingType(videoEndings, endings)) keywords.add("file:video");
        if (hasEndingType(imageEndings, endings)) keywords.add("file:image");
        if (hasEndingType(documentEndings, endings)) keywords.add("file:document");
        if (hasEndingType(archiveEndings, endings)) keywords.add("file:archive");

        return keywords;
    }
    private static final Set<String> ignoredEndings = Set.of("html",
            "htm", "cfm", "php", "asp", "aspx", "jsp", "shtml",
            "xhtml", "com", "org", "cgi", "net", "edu", "gov", "jp", "nl",
            "ly", "co", "io", "dev", "rss", "xml");


    private static final String[] videoEndings = new String[] {
            "avi", "mp4", "mov", "wmv", "flv", "mkv", "mpg", "mpeg", "m4v", "webm", "3gp"
    };
    private static final String[] audioEndings = new String[] {
            "mp3", "wav", "ogg", "wma", "aac", "flac", "m4a", "mid", "midi", "aiff", "aif", "aifc", "au", "snd", "amr", "oga", "opus"
    };

    private static final String[] imageEndings = new String[] {
            "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "svg", "webp"
    };

    private static final String[] documentEndings = new String[] {
            "pdf", "doc", "docx", "xls", "xslx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "txt", "csv", "tsv"
    };

    private static final String[] archiveEndings = new String[] {
            "zip", "rar", "gz", "tar", "7z", "bz2", "xz", "bz2", "iso", "dmg", "pkg", "deb", "rpm", "apk", "jar", "war", "ear", "tgz"
    };

    private static boolean hasEndingType(String[] included, Set<String> endings) {
        for (var ending : included) {
            if (endings.contains(ending)) return true;
        }

        return false;
    }


}
