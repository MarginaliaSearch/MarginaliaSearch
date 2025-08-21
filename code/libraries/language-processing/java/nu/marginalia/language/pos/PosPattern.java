package nu.marginalia.language.pos;

import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PosPattern {
    public final LongArrayList pattern = new LongArrayList();

    public long[] toArray() {
        return pattern.toLongArray();
    }

    public int size() {
        return pattern.size();
    }

    public PosPattern(Map<String, Integer> lexicon, String expression) {
        List<String> tokens = new ArrayList<>();
        int pos = 0;

        while (pos < expression.length()) {
            char c = expression.charAt(pos);
            if ("()".indexOf(c) >= 0) {
                tokens.add(expression.substring(pos, pos + 1));
                pos++;
            }
            else if (Character.isSpaceChar(c)) {
                pos++;
            }
            else {
                int end =  pos + 1;
                while (end <  expression.length()) {
                    int ce = expression.charAt(end);
                    if ("() ".indexOf(ce) >= 0) {
                        break;
                    }
                    else {
                        end++;
                    }
                }
                tokens.add(expression.substring(pos, end));
                pos = end;
            }
        }

        List<List<String>> variantsPerPos = new ArrayList<>();
        boolean inParen = false;
        for (String token : tokens) {
            if ("(".equalsIgnoreCase(token)) {
                inParen = true;
                variantsPerPos.addLast(new ArrayList<>());
            }
            else if (")".equals(token)) {
                inParen = false;
            }
            else if (!inParen) {
                variantsPerPos.addLast(List.of(token));
            }
            else if (inParen) {
                variantsPerPos.getLast().add(token);
            }
        }

        for (List<String> variants : variantsPerPos) {
            long variantsCompiled = 0L;
            for (String variant : variants) {
                if (variant.endsWith("*") && variant.length() > 1) {
                    String prefix = variant.substring(0, variant.length() - 1);
                    variantsCompiled |= lexicon.entrySet()
                            .stream()
                            .filter(entry -> entry.getKey().startsWith(prefix))
                            .mapToLong(entry -> 1L<<entry.getValue())
                            .reduce(0, (a,b) -> (a|b));
                }
                else {
                    Integer bit = lexicon.get(variant);
                    if (bit != null) {
                        variantsCompiled |= 1L << bit;
                    }
                }
            }
            pattern.add(variantsCompiled);
        }
    }
}
