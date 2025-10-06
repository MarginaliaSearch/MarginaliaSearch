package nu.marginalia.language.encoding;

import java.util.regex.Pattern;

public interface UnicodeNormalization {

    String flattenUnicode(String s);
    Pattern wordBreakPattern();

    static Pattern basicWordBreaks = Pattern.compile("((\\s+[(]?)|[|,]|([.;\\-()]+(\\s+|$)))");
    static Pattern europeanWordBreaks = Pattern.compile("([^/<>$:_#@.a-zA-Z'+\\-0-9\\u00C0-\\u00D6\\u00D8-\\u00f6\\u00f8-\\u00ff]+)|[|]|(\\.(\\s+|$))");

    static final boolean NO_FLATTEN_UNICODE =
            Boolean.getBoolean("system.noFlattenUnicode");

    class JustNormalizeQuotes implements UnicodeNormalization {

        public Pattern wordBreakPattern() {
            return basicWordBreaks;
        }

        public String flattenUnicode(String s) {
            if (NO_FLATTEN_UNICODE)
                return s;

            if (isPlainAscii(s)) {
                return s;
            }

            StringBuilder sb = new StringBuilder(s.length() + 10);

            for (int i = 0; i < s.length(); ) {
                int c = s.codePointAt(i);
                i += Character.charCount(c);

                if ("\u201C\u201D".indexOf(c) >= 0) {
                    sb.append('"');
                }
                else {
                    sb.appendCodePoint(c);
                }
            }

            return sb.toString();
        }
    }

    class FlattenEAccents implements UnicodeNormalization {
        public Pattern wordBreakPattern() {
            if (NO_FLATTEN_UNICODE)
                return basicWordBreaks;

            return europeanWordBreaks;
        }

        public String flattenUnicode(String s) {
            if (NO_FLATTEN_UNICODE)
                return s;

            if (isPlainAscii(s)) {
                return s;
            }

            StringBuilder sb = new StringBuilder(s.length() + 10);

            int numCp = s.codePointCount(0, s.length());

            for (int i = 0; i < numCp;) {
                int c = s.codePointAt(i);
                i+=Character.charCount(c);

                if ("\u201C\u201D".indexOf(c) >= 0) {
                    sb.append('"');
                }
                else if ("é".indexOf(c) >= 0) {
                    sb.append('e');
                }
                else {
                    sb.appendCodePoint(c);
                }
            }

            return sb.toString();
        }
    }

    class Flattenß implements UnicodeNormalization {

        public Pattern wordBreakPattern() {
            if (NO_FLATTEN_UNICODE)
                return basicWordBreaks;

            return europeanWordBreaks;
        }

        public String flattenUnicode(String s) {
            if (NO_FLATTEN_UNICODE)
                return s;

            if (isPlainAscii(s)) {
                return s;
            }

            StringBuilder sb = new StringBuilder(s.length() + 10);

            for (int i = 0; i < s.length(); ) {
                int c = s.codePointAt(i);
                i += Character.charCount(c);

                if ("\u201C\u201D".indexOf(c) >= 0) {
                    sb.append('"');
                } else if ('ß' == c) {
                    sb.append("ss");
                }
                else {
                    sb.appendCodePoint(c);
                }
            }

            return sb.toString();
        }
    }

    class FlattenAllLatin implements UnicodeNormalization {
        public Pattern wordBreakPattern() {
            if (NO_FLATTEN_UNICODE)
                return basicWordBreaks;

            return europeanWordBreaks;
        }

        public String flattenUnicode(String s) {
            if (NO_FLATTEN_UNICODE)
                return s;

            if (isPlainAscii(s)) {
                return s;
            }

            StringBuilder sb = new StringBuilder(s.length() + 10);

            // Falsehoods programmers believe about the latin alphabet ;-)
            for (int i = 0; i < s.length(); ) {
                int c = s.codePointAt(i);
                i += Character.charCount(c);

                if ("\u201C\u201D".indexOf(c) >= 0) {
                    sb.append('"');
                }
                else if ("áâàȁăåäāǟãąą̊ḁẚⱥ".indexOf(c) >= 0) {
                    sb.append('a');
                }
                else if ("ḃḅḇƀɓ".indexOf(c) >= 0) {
                    sb.append('b');
                }
                else if ("ćĉčçḉċƈȼ".indexOf(c) >= 0) {
                    sb.append('c');
                }
                else if ("ɗḓďḋḍḏḑđðɖḏ".indexOf(c) >= 0) {
                    sb.append('d');
                }
                else if ("éêèȅěëēẽĕęėẹȇḕḗḙḛḝɇ".indexOf(c) >= 0) {
                    sb.append('e');
                }
                else if ("ḟƒ".indexOf(c) >= 0) {
                    sb.append('f');
                }
                else if ("ǵĝǧğġģɠḡǥ".indexOf(c) >= 0) {
                    sb.append('g');
                }
                else if ("ĥȟḧḣḥẖḩḫħⱨ".indexOf(c) >= 0) {
                    sb.append('g');
                }
                else if ("iıíîìȉïḯīĩįịḭ".indexOf(c) >= 0) {
                    sb.append('i');
                }
                else if ("ĵǰɉ".indexOf(c) >= 0) {
                    sb.append('j');
                }
                else if ("ḱǩķḳḵƙⱪ".indexOf(c) >= 0) {
                    sb.append('k');
                }
                else if ("ĺłḽľļḷḹḻƚɫⱡ".indexOf(c) >= 0) {
                    sb.append('l');
                }
                else if ("ḿṁṃ".indexOf(c) >= 0) {
                    sb.append('m');
                }
                else if ("ŋńǹñṋňṅṇṉŉn̈ņ".indexOf(c) >= 0) {
                    sb.append('n');
                }
                else if ("óőôòȍŏȯȱöȫōṓṑõṍṏȭøǿǫǭọȏơ".indexOf(c) >= 0) {
                    sb.append('o');
                }
                else if ("ṕṗƥᵽ".indexOf(c) >= 0) {
                    sb.append('p');
                }
                else if ("ꝗ".indexOf(c) >= 0) {
                    sb.append('q');
                }
                else if ("ŕȑřŗṙṛṝṟɍɽ".indexOf(c) >= 0) {
                    sb.append('r');
                }
                else if ("śṥŝšṧşșṡṣṩ".indexOf(c) >= 0) {
                    sb.append('s');
                }
                else if ("ťṱẗţțŧṫṭṯⱦ".indexOf(c) >= 0) {
                    sb.append('t');
                }
                else if ("úùûŭưűüūṻųůũṹụṳṵṷʉ".indexOf(c) >= 0) {
                    sb.append('u');
                }
                else if ("ṽṿʋỽ".indexOf(c) >= 0) {
                    sb.append('v');
                }
                else if ("ẃŵẁẅẘẇẉⱳ".indexOf(c) >= 0) {
                    sb.append('w');
                }
                else if ("x̂ẍẋ".indexOf(c) >= 0) {
                    sb.append('x');
                }
                else if ("ƴýŷỳÿȳỹẙẏy̨ɏỿ".indexOf(c) >= 0) {
                    sb.append('y');
                }
                else if ("źẑžżẓẕƶȥ".indexOf(c) >= 0) {
                    sb.append('z');
                }
                else if ("Þþ".indexOf(c) >= 0) {
                    sb.append("th");
                }
                else if ('ß' == c) {
                    sb.append("ss");
                }
                else if (isAscii(c)) {
                    sb.append((char) c);
                }
            }

            return sb.toString();
        }

    }

    private static boolean isPlainAscii(String s) {
        for (int i = 0; i < s.length(); ) {
            int c = s.codePointAt(i);
            if (!isAscii(c))
                return false;
            i += Character.charCount(c);
        }
        return true;
    }

    private static boolean isAscii(int c) {
        return (c & ~0x7f) == 0;
    }


}
