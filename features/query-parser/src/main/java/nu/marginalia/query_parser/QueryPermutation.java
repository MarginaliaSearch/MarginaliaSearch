package nu.marginalia.query_parser;

import nu.marginalia.language.WordPatterns;
import nu.marginalia.query_parser.token.Token;
import nu.marginalia.query_parser.token.TokenType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Stream.concat;

public class QueryPermutation {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final QueryVariants queryVariants;


    public QueryPermutation(QueryVariants queryVariants) {
        this.queryVariants = queryVariants;
    }

    public List<List<Token>> permuteQueries(List<Token> items) {
        int start = -1;
        int end = items.size();

        for (int i = 0; i < items.size(); i++) {
            var token = items.get(i);

            if (start < 0) {
                if (token.type == TokenType.LITERAL_TERM && WordPatterns.wordQualitiesPredicate.test(token.str)) {
                    start = i;
                }
            }
            else {
                if (token.type != TokenType.LITERAL_TERM || !WordPatterns.wordPredicateEither.test(token.str)) {
                    end = i;
                    break;
                }
            }
        }

        if (start >= 0 && end - start > 1) {
            List<List<Token>> permuteParts = combineSearchTerms(items.subList(start, end));
            int s = start;
            int e = end;
            return permuteParts.stream().map(part ->
                            concat(items.subList(0, s).stream(), concat(part.stream(), items.subList(e, items.size()).stream()))
                                    .collect(Collectors.toList()))
                    .peek(lst -> lst.removeIf(this::isJunkWord))
                    .limit(24)
                    .collect(Collectors.toList());
        }
        else {
            return List.of(items);
        }
    }


    public List<List<Token>> permuteQueriesNew(List<Token> items) {
        int start = -1;
        int end = items.size();

        for (int i = 0; i < items.size(); i++) {
            var token = items.get(i);

            if (start < 0) {
                if (token.type == TokenType.LITERAL_TERM && WordPatterns.wordQualitiesPredicate.test(token.str)) {
                    start = i;
                }
            }
            else {
                if (token.type != TokenType.LITERAL_TERM || !WordPatterns.wordPredicateEither.test(token.str)) {
                    end = i;
                    break;
                }
            }
        }

        if (start >= 0 && end - start >= 1) {
            var result = queryVariants.getQueryVariants(items.subList(start, end));

            logger.debug("{}", result);

            if (result.isEmpty()) {
                logger.warn("Empty variants result, falling back on old code");
                return permuteQueries(items);
            }

            List<List<Token>> queryVariants = new ArrayList<>();
            for (var query : result.faithful) {
                var tokens = query.terms.stream().map(term -> new Token(TokenType.LITERAL_TERM, term)).collect(Collectors.toList());
                tokens.addAll(result.nonLiterals);

                queryVariants.add(tokens);
            }
            for (var query : result.alternative) {
                if (queryVariants.size() >= 6)
                    break;

                var tokens = query.terms.stream().map(term -> new Token(TokenType.LITERAL_TERM, term)).collect(Collectors.toList());
                tokens.addAll(result.nonLiterals);

                queryVariants.add(tokens);
            }

            List<List<Token>> returnValue = new ArrayList<>(queryVariants.size());
            for (var variant: queryVariants) {
                List<Token> r = new ArrayList<>(start + variant.size() + (items.size() - end));
                r.addAll(items.subList(0, start));
                r.addAll(variant);
                r.addAll(items.subList(end, items.size()));
                returnValue.add(r);
            }

            return returnValue;

        }
        else {
            return List.of(items);
        }
    }

    private boolean isJunkWord(Token token) {
        if (WordPatterns.isStopWord(token.str) &&
                !token.str.matches("^(\\d+|([a-z]+:.*))$")) {
            return true;
        }
        return switch (token.str) {
            case "vs", "versus", "or", "and" -> true;
            default -> false;
        };
    }

    private List<List<Token>> combineSearchTerms(List<Token> subList) {
        int size = subList.size();
        if (size < 1) {
            return Collections.emptyList();
        }
        else if (size == 1) {
            if (WordPatterns.isStopWord(subList.get(0).str)) {
                return Collections.emptyList();
            }
            return List.of(subList);
        }

        List<List<Token>> results = new ArrayList<>(size*(size+1)/2);

        if (subList.size() <= 4 && subList.get(0).str.length() >= 2 && !isPrefixWord(subList.get(subList.size()-1).str)) {
            results.add(List.of(joinTokens(subList)));
        }
        outer: for (int i = size - 1; i >= 1; i--) {

            var left = combineSearchTerms(subList.subList(0, i));
            var right = combineSearchTerms(subList.subList(i, size));

            for (var l : left) {
                if (results.size() > 48) {
                    break outer;
                }

                for (var r : right) {
                    if (results.size() > 48) {
                        break  outer;
                    }

                    List<Token> combined = new ArrayList<>(l.size() + r.size());
                    combined.addAll(l);
                    combined.addAll(r);
                    if (!results.contains(combined)) {
                        results.add(combined);
                    }
                }
            }
        }
        if (!results.contains(subList)) {
            results.add(subList);
        }
        Comparator<List<Token>> tc = (o1, o2) -> {
            int dJoininess = o2.stream().mapToInt(s->(int)Math.pow(joininess(s.str), 2)).sum() -
                    o1.stream().mapToInt(s->(int)Math.pow(joininess(s.str), 2)).sum();
            if (dJoininess == 0) {
                return (o2.stream().mapToInt(s->(int)Math.pow(rightiness(s.str), 2)).sum() -
                        o1.stream().mapToInt(s->(int)Math.pow(rightiness(s.str), 2)).sum());
            }
            return (int) Math.signum(dJoininess);
        };
        results.sort(tc);
        return results;
    }

    private boolean isPrefixWord(String str) {
        return switch (str) {
            case "the", "of", "when" -> true;
            default -> false;
        };
    }

    int joininess(String s) {
        return (int) s.chars().filter(c -> c == '_').count();
    }
    int rightiness(String s) {
        int rightiness = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '_') {
                rightiness+=i;
            }
        }
        return rightiness;
    }

    private Token joinTokens(List<Token> subList) {
        return new Token(TokenType.LITERAL_TERM,
                subList.stream().map(t -> t.str).collect(Collectors.joining("_")),
                subList.stream().map(t -> t.str).collect(Collectors.joining(" ")));
    }
}
