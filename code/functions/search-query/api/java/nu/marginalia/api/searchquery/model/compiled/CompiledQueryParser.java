package nu.marginalia.api.searchquery.model.compiled;

import org.apache.commons.lang3.StringUtils;

import java.util.*;

/** Parser for a compiled index query */
public class CompiledQueryParser {

    public static CompiledQuery<String> parse(String query) {
        List<String> parts = tokenize(query);

        if (parts.isEmpty()) {
            return new CompiledQuery<>(
                    CqExpression.empty(),
                    new CqData<>(new String[0])
            );
        }

        // We aren't interested in a binary tree representation, but an n-ary tree one,
        // so a somewhat unusual parsing technique is used to avoid having an additional
        // flattening step at the end.

        // This is only possible due to the trivial and unambiguous grammar of the compiled queries

        List<AndOrState> parenState = new ArrayList<>();
        parenState.add(new AndOrState());

        Map<String, Integer> wordIds = new HashMap<>();

        for (var part : parts) {
            var head = parenState.getLast();

            if (part.equals("|")) {
                head.or();
            }
            else if (part.equals("(")) {
                parenState.addLast(new AndOrState());
            }
            else if (part.equals(")")) {
                if (parenState.size() < 2) {
                    throw new IllegalStateException("Mismatched parentheses in expression: " + query);
                }
                parenState.removeLast();
                parenState.getLast().and(head.closeOr());
            }
            else {
                head.and(
                        new CqExpression.Word(
                                wordIds.computeIfAbsent(part, p -> wordIds.size())
                        )
                );
            }
        }

        if (parenState.size() != 1)
            throw new IllegalStateException("Mismatched parentheses in expression: " + query);

        // Construct the CompiledQuery object with String:s as leaves
        var root = parenState.getLast().closeOr();

        String[] cqData = new String[wordIds.size()];
        wordIds.forEach((w, i) -> cqData[i] = w);
        return new CompiledQuery<>(root, new CqData<>(cqData));

    }

    private static class AndOrState {
        private List<CqExpression> andState = new ArrayList<>();
        private List<CqExpression> orState = new ArrayList<>();

        /** Add a new item to the and-list */
        public void and(CqExpression e) {
            andState.add(e);
        }

        /** Turn the and-list into an expression on the or-list, and then start a new and-list */
        public void or() {
            closeAnd();

            andState = new ArrayList<>();
        }

        /** Turn the and-list into an And-expression in the or-list */
        private void closeAnd() {
            if (andState.size() == 1)
                orState.add(andState.getFirst());
            else if (!andState.isEmpty())
                orState.add(new CqExpression.And(andState));
        }

        /** Finalize the current and-list, then turn the or-list into an Or-expression */
        public CqExpression closeOr() {
            closeAnd();

            if (orState.isEmpty())
                return CqExpression.empty();
            if (orState.size() == 1)
                return orState.getFirst();

            return new CqExpression.Or(orState);
        }
    }

    private static List<String> tokenize(String query) {
        // Each token is guaranteed to be separated by one or more space characters

        return Arrays.stream(StringUtils.split(query, ' '))
                .filter(StringUtils::isNotBlank)
                .toList();
    }

}
