package nu.marginalia.language.encoding;

public class AsciiFlattener {

    public static String flattenUnicode(String s) {

        if (isPlainAscii(s)) {
            return s;
        }

        StringBuilder sb = new StringBuilder(s.length());

        int numCp = s.codePointCount(0, s.length());

        // Falsehoods programmers believe about the latin alphabet ;-)

        for (int i = 0; i < numCp; i++) {
            int c = s.codePointAt(i);

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

    private static boolean isPlainAscii(String s) {
        int i;

        int numCp = s.codePointCount(0, s.length());

        for (i = 0; i < numCp && isAscii(s.codePointAt(i)); i++);

        return i == s.length();
    }

    private static boolean isAscii(int c) {
        return (c & ~0x7f) == 0;
    }


}
