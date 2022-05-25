package nu.marginalia.util.language.processing;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public class AsciiFlattener {

    private static final Pattern nonAscii = Pattern.compile("[^a-zA-Z0-9_.'+@#:]+");
    private static final Pattern plainAsciiPattern = Pattern.compile("^[a-zA-Z0-9_.'+@#:]+$");
    private static final Predicate<String> plainAscii = plainAsciiPattern.asMatchPredicate();

    public static String flattenUnicode(String s) {
        if (plainAscii.test(s)) {
            return s;
        }

        var cdata = s.toCharArray();
        var newCdata = new char[cdata.length];
        for (int i = 0; i < cdata.length; i++) {
            if ("àáâãäåæ".indexOf(cdata[i]) >= 0) {
                newCdata[i] = 'a';
            }
            else if ("ç".indexOf(cdata[i]) >= 0) {
                newCdata[i] = 'g';
            }
            else if ("òóôõöø".indexOf(cdata[i]) >= 0) {
                newCdata[i] = 'o';
            }
            else if ("ùúûü".indexOf(cdata[i]) >= 0) {
                newCdata[i] = 'u';
            }
            else if ("ýÿÞþ".indexOf(cdata[i]) >= 0) {
                newCdata[i] = 'y';
            }
            else if ("ìíîï".indexOf(cdata[i]) >= 0) {
                newCdata[i] = 'i';
            }
            else if ("èéêë".indexOf(cdata[i]) >= 0) {
                newCdata[i] = 'e';
            }
            else if ("ß".indexOf(cdata[i]) >= 0) {
                newCdata[i] = 's';
            }
            else {
                newCdata[i] = cdata[i];
            }
        }
        return nonAscii.matcher(new String(newCdata)).replaceAll("");
    }

}
