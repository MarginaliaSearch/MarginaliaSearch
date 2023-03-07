package nu.marginalia.util;

import java.util.ArrayList;
import java.util.List;

public class LineUtils {

    /** LF, CR, CRLF, LFCR-agnostic string-line splitter that preserves empty lines
     * that does not create a huge blob of a last item like String$split(regex, n)
     *
     */
    public static List<String> firstNLines(String documentBody, int numLines) {
        List<String> lines = new ArrayList<>(numLines);

        boolean eatCr = false;
        boolean eatLf = false;
        int startPos = 0;

        for (int pos = 0; pos < documentBody.length() && lines.size() < numLines; pos++) {
            int cp = documentBody.charAt(pos);
            if (cp == '\r') {
                if (eatCr) {
                    eatCr = false;
                }
                else {
                    eatLf = true;
                    lines.add(documentBody.substring(startPos, pos));
                }
                startPos = pos + 1;
            } else if (cp == '\n') {
                if (eatLf) {
                    eatLf = false;
                }
                else {
                    eatCr = true;
                    lines.add(documentBody.substring(startPos, pos));
                }
                startPos = pos + 1;
            } else {
                eatCr = eatLf = false;
            }
        }

        return lines;
    }

}
