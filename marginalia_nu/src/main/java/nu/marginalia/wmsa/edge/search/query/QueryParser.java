package nu.marginalia.wmsa.edge.search.query;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import nu.marginalia.util.TransformList;
import nu.marginalia.util.language.WordPatterns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Stream.concat;

public class QueryParser {
    private static final Logger logger = LoggerFactory.getLogger(QueryParser.class);

    private final EnglishDictionary englishDictionary;
    private final QueryVariants queryVariants;

    public QueryParser(EnglishDictionary englishDictionary, QueryVariants queryVariants) {
        this.englishDictionary = englishDictionary;
        this.queryVariants = queryVariants;
    }

    public List<Token> parse(String query) {
        List<Token> basicTokens = extractBasicTokens(query);

        TransformList<Token> list = new TransformList<>(basicTokens);

        list.transformEach(QueryParser::handleQuoteTokens);
        list.transformEach(QueryParser::trimLiterals);
        list.transformEachPair(QueryParser::createNegatedTerms);
        list.transformEachPair(QueryParser::createPriorityTerms);
        list.transformEach(QueryParser::handleSpecialOperations);
        list.scanAndTransform(TokenType.LPAREN, TokenType.RPAREN, QueryParser::handleAdvisoryTerms);

        return list.getBackingList();
    }

    private static void handleQuoteTokens(TransformList<Token>.Entity entity) {
        var t = entity.value;
        if (t.type == TokenType.QUOT) {
            entity.replace(new Token(TokenType.QUOT_TERM,
                    t.str.replaceAll("\\s+", WordPatterns.WORD_TOKEN_JOINER),
                    t.displayStr));
        }
    }

    private static void trimLiterals(TransformList<Token>.Entity entity) {
        var t = entity.value;

        if (t.type == TokenType.LITERAL_TERM
                && (t.str.endsWith(":") || t.str.endsWith("."))
                && t.str.length() > 1) {
            entity.replace(new Token(TokenType.LITERAL_TERM, t.str.substring(0, t.str.length() - 1), t.displayStr));
        }

    }

    private static void createNegatedTerms(TransformList<Token>.Entity first, TransformList<Token>.Entity second) {
        var t = first.value;
        var tn = second.value;

        if (t.type == TokenType.MINUS && tn.type == TokenType.LITERAL_TERM) {
            first.remove();
            second.replace(new Token(TokenType.EXCLUDE_TERM, tn.str, "-" + tn.str));
        }
    }
    private static void createPriorityTerms(TransformList<Token>.Entity first, TransformList<Token>.Entity second) {
        var t = first.value;
        var tn = second.value;

        if (t.type == TokenType.QMARK && tn.type == TokenType.LITERAL_TERM) {
            first.remove();
            second.replace(new Token(TokenType.PRIORTY_TERM, tn.str, "?" + tn.str));
        }
    }
    private static void handleSpecialOperations(TransformList<Token>.Entity entity) {
        var t = entity.value;
        if (t.type == TokenType.LITERAL_TERM) {
            if (t.str.startsWith("q") && t.str.matches("q[=><]\\d+")) {
                entity.replace(new Token(TokenType.QUALITY_TERM, t.str.substring(1), t.displayStr));
            } else if (t.str.startsWith("near:")) {
                entity.replace(new Token(TokenType.NEAR_TERM, t.str.substring(5), t.displayStr));
            } else if (t.str.startsWith("year") && t.str.matches("year[=><]\\d{4}")) {
                entity.replace(new Token(TokenType.YEAR_TERM, t.str.substring(4), t.displayStr));
            } else if (t.str.startsWith("size") && t.str.matches("size[=><]\\d+")) {
                entity.replace(new Token(TokenType.SIZE_TERM, t.str.substring(4), t.displayStr));
            } else if (t.str.contains(":")) {
                entity.replace(new Token(TokenType.ADVICE_TERM, t.str, t.displayStr));
            }
        }
    }

    private static void handleAdvisoryTerms(TransformList<Token>.Entity entity) {
        var t = entity.value;
        if (t.type == TokenType.LPAREN) {
            entity.remove();
        } else if (t.type == TokenType.RPAREN) {
            entity.remove();
        } else if (t.type == TokenType.LITERAL_TERM) {
            entity.replace(new Token(TokenType.ADVICE_TERM, t.str, "(" + t.str + ")"));
        }
    }

    private static final Pattern noisePattern = Pattern.compile("[,]");

    public List<Token> extractBasicTokens(String rawQuery) {
        List<Token> tokens = new ArrayList<>();

        String query = noisePattern.matcher(rawQuery).replaceAll(" ");

        for (int i = 0; i < query.length(); i++) {
            int chr = query.charAt(i);

            if ('(' == chr) {
                tokens.add(new Token(TokenType.LPAREN, query.substring(i, i+1).toLowerCase(), query.substring(i, i+1)));
            }
            else if (')' == chr) {
                tokens.add(new Token(TokenType.RPAREN, query.substring(i, i+1).toLowerCase(), query.substring(i, i+1)));
            }
            else if ('"' == chr) {
                int end = query.indexOf('"', i+1);
                if (end == -1) {
                    end = query.length();
                }
                tokens.add(new Token(TokenType.QUOT,
                        query.substring(i+1, end).toLowerCase(),
                        query.substring(i, Math.min(query.length(), end+1))));
                i = end;
            }
            else if ('\u201C' == chr) {
                int end = query.indexOf('\u201D', i+1);
                if (end == -1) {
                    end = query.length();
                }
                tokens.add(new Token(TokenType.QUOT,
                        query.substring(i+1, end).toLowerCase(),
                        query.substring(i, Math.min(query.length(), end+1))));
                i = end;
            }
            else if ('-' == chr) {
                tokens.add(new Token(TokenType.MINUS, "-"));
            }
            else if ('?' == chr) {
                tokens.add(new Token(TokenType.QMARK, "?"));
            }
            else if (Character.isSpaceChar(chr)) {
                //
            }
            else {

                int end = i+1;
                for (; end < query.length(); end++) {
                    if (query.charAt(end) == ' ' || query.charAt(end) == ')')
                        break;
                }
                tokens.add(new Token(TokenType.LITERAL_TERM,
                        query.substring(i, end).toLowerCase(),
                        query.substring(i, end)));
                i = end-1;
            }
        }
        return tokens;
    }


    public List<List<Token>> variantQueries(List<Token> items) {
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
            List<List<Token>> variantParts = getVariantSearchTerms(items.subList(start, end));
            int s = start;
            int e = end;
            return variantParts.stream().map(part ->
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

    private List<List<Token>> getVariantSearchTerms(List<Token> subList) {
        int size = subList.size();
        if (size < 1) {
            return Collections.emptyList();
        }
        else if (size == 1) {
            if (WordPatterns.isStopWord(subList.get(0).str)) {
                return Collections.emptyList();
            }
            return getWordVariants(subList.get(0)).map(List::of).collect(Collectors.toList());
        }

        List<List<Token>> cdrs = getVariantSearchTerms(subList.subList(1, subList.size()));
        List<Token> cars = getWordVariants(subList.get(0)).collect(Collectors.toList());

        List<List<Token>> ret = new ArrayList<>(cars.size() * cdrs.size());
        for (var car : cars) {
            if (ret.size() >= 32) {
                break;
            }
            for (var cdr : cdrs) {
                ret.add(List.of(joinTokens(prepend(car, cdr))));
            }
        }
        return ret;
    }

    private Stream<Token> getWordVariants(Token token) {
        var s = token.str;
        int sl = s.length();
        Stream<Token> base = Stream.of(token);
        Stream<String> alternatives;
        if (sl < 2) {
            return base;
        }
        if (s.endsWith("s")) {
            alternatives = Stream.of(s.substring(0, sl-1), s + "es");
        }
        else if (s.matches(".*(\\w)\\1ing$") && sl > 4) { // humming, clapping
            var basea = s.substring(0, sl-4);
            var baseb = s.substring(0, sl-3);
            alternatives = Stream.of(basea, baseb + "ed");
        }
        else {
            alternatives = Stream.of(s+"s", s+"ing", s+"ed");
        }

        return Stream.concat(Stream.of(token), alternatives.filter(englishDictionary::isWord).map(str -> new Token(token.type, str, token.displayStr)));
    }

    private List<Token> prepend(Token t, List<Token> lst) {
        List<Token> ret = new ArrayList<>(lst.size() + 1);
        ret.add(t);
        ret.addAll(lst);
        return ret;
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

    private boolean isSuffixWord(String str) {
        return (str.length() < 2);
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

@ToString @EqualsAndHashCode
class Token {
    public TokenType type;
    public String str;
    public final String displayStr;

    Token(TokenType type, String str, String displayStr) {
        this.type = type;
        this.str = str;
        this.displayStr = safeString(displayStr);
    }


    Token(TokenType type, String str) {
        this.type = type;
        this.str = str;
        this.displayStr = safeString(str);
    }

    private static String safeString(String s) {
        return s.replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;");
    }
}

enum TokenType implements Predicate<Token> {
    TERM,


    LITERAL_TERM,
    QUOT_TERM,
    EXCLUDE_TERM,
    ADVICE_TERM,
    PRIORTY_TERM,

    QUALITY_TERM,
    YEAR_TERM,
    SIZE_TERM,
    NEAR_TERM,

    QUOT,
    MINUS,
    QMARK,
    LPAREN,
    RPAREN,

    IGNORE;

    public boolean test(Token t) {
        return t.type == this;
    }
}