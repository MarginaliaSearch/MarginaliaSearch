package nu.marginalia.wmsa.edge.crawler.domain.language;

public enum UnicodeRanges {
    GREEK(false, 0x0370,0x03FF),
    CYRILLIC(false, 0x0400,0x04FF),
    CYRILLIC2(false, 0x0500,0x052F),
    ARMENIAN(false, 0x0530,0x058F),
    HEBREW(false, 0x0590,0x05FF),
    ARABIC(false, 0x0600,0x06FF),
    SYRIAC(false, 0x0700,0x074F),
    THAANA(false, 0x0780,0x07BF),
    DEVANAGARI(false, 0x0900,0x097F),
    BENGALI(false, 0x0980,0x09FF),
    GURMUKHI(false, 0x0A00,0x0A7F),
    GUJARATI(false, 0x0A80,0x0AFF),
    ORIYA(false, 0x0B00,0x0B7F),
    TAMIL(false, 0x0B80,0x0BFF),
    TELUGU(false, 0x0C00,0x0C7F),
    KANNADA(false, 0x0C80,0x0CFF),
    MALAYALAM(false, 0x0D00,0x0D7F),
    SINHALA(false, 0x0D80,0x0DFF),
    THAI(false, 0x0E00,0x0E7F),
    LAO(false, 0x0E80,0x0EFF),
    TIBETAN(false, 0x0F00,0x0FFF),
    MYANMAR(false, 0x1000,0x109F),
    GEORGIAN(false, 0x10A0,0x10FF),
    HANGUL(false, 0x1100,0x11FF),
    ETHIOPIC(false, 0x1200,0x137F),
    CHEROKEE(false, 0x13A0,0x13FF),
    ABORIGINAL(false, 0x1400,0x167F),
    OGHAM(false, 0x1680,0x169F),
    RUNIC(false, 0x16A0,0x16FF),
    TAGALOG(false, 0x1700,0x171F),
    HANUNOO(false, 0x1720,0x173F),
    BUHID(false, 0x1740,0x175F),
    TAGBANWA(false, 0x1760,0x177F),
    KHMER(false, 0x1780,0x17FF),
    MONGOLIAN(false, 0x1800,0x18AF),
    LIMBU(false, 0x1900,0x194F),
    TAILE(false, 0x1950,0x197F),
    KHMER2(false, 0x19E0,0x19FF),
    CJKRADICALS(true,0x2E80,0x2EFF),
    KANGXIRADICALS(true, 0x2F00,0x2FDF),
    IDEOGRAPHICDESCRIPTION(true,0x2FF0,0x2FFF),
    CJKSYMBOLS(true, 0x3000,0x303F),
    HIRAGANA(true,0x3040,0x309F),
    KATAKANA(true,0x30A0,0x30FF),
    BOPOMOFO(true,0x3100,0x312F),
    HANGULJAMO(true,0x3130,0x318F),
    KANBUN(true,0x3190,0x319F),
    BOPOMOFOEXTENDED(true,0x31A0,0x31BF),
    KATAKANAPHONETIC(true, 0x31F0,0x31FF),
    ENCLOSEDCJK(true, 0x3200,0x32FF),
    CJKCOMPATIBILITY(true,0x3300,0x33FF),
    CJKUNIFIEDA(true,0x3400,0x4DBF),
    YIJINGHEXAGRAMSYMBOLS(true,0x4DC0,0x4DFF),
    CJKUNIFIEDIDEOGRAPHS(true,0x4E00,0x9FFF),
    YISYLLABLES(true,0xA000,0xA48F),
    YIRADICALS(true, 0xA490,0xA4CF),
    HANGULSYLLABLES(true, 0xAC00,0xD7AF)
    ;
    final int min;
    final int max;
    final boolean sensitive;
    UnicodeRanges(boolean sensitive, int min, int max) {
        this.sensitive = sensitive;
        this.min = min;
        this.max = max;
    }

    boolean test(String text) {
        return text.chars().limit(1000).parallel()
                .filter(i -> i >= min && i < max)
                .count() >= (sensitive ? 15 : 100);

    }
}
