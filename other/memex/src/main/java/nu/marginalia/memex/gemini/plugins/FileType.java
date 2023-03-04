package nu.marginalia.memex.gemini.plugins;

import java.nio.file.Path;

public enum FileType {
    GMI("gmi", "text/gemini", FileIcons.DOCUMENT, false),
    GEM("gem", "text/gemini", FileIcons.DOCUMENT, false),
    TXT("txt", "text/plain", FileIcons.DOCUMENT, false),
    MARKDOWN("md", "text/markdown", FileIcons.DOCUMENT, false),
    JAVA("java", "text/java", FileIcons.JAVA, false),
    PROPERTIES("properties", "text/properties", FileIcons.SETTINGS, false),
    GRADLE("gradle", "text/gradle", FileIcons.SETTINGS, false),
    ZIP("zip", "application/zip", FileIcons.ZIP, true),
    PNG("png", "image/png", FileIcons.IMAGE, true),
    JPG("jpg", "image/jpg", FileIcons.IMAGE, true),
    JPEG("jpeg", "image/jpg", FileIcons.IMAGE, true),
    BIN("bin", "application/binary", FileIcons.BINARY, true),
    SH("sh", "text/sh", FileIcons.SETTINGS, false),
    XML("xml", "text/xml", FileIcons.DOCUMENT, false),
    DOCKERFILE("Dockerfile", "text/dockerfile", FileIcons.SETTINGS, false)
    ;

    public static FileType match(String fileName) {
        for (var type : values()) {
            if (fileName.endsWith(type.suffix)) {
                return type;
            }
        }
        return BIN;
    }

    public static FileType match(Path path) {
        return match(path.toString());
    }

    FileType(String suffix, String mime, String icon, boolean binary) {
        this.suffix = suffix;
        this.mime = mime;

        this.icon = icon;
        this.binary = binary;
    }
    public final String suffix;
    public final String mime;
    public final String icon;
    public final boolean binary;

}

class FileIcons {
    public static final String DOCUMENT = "🗒";
    public static final String JAVA = "♨";
    public static final String SETTINGS = "💻";
    public static final String ZIP = "🗜";
    public static final String IMAGE = "🖼";
    public static final String DIRECTORY = "🗂";
    public static final String BINARY = "📚";
}
