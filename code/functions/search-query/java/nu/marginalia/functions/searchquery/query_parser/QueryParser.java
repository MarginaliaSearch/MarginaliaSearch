package nu.marginalia.functions.searchquery.query_parser;

import nu.marginalia.functions.searchquery.query_parser.token.QueryToken;
import nu.marginalia.index.query.limit.SpecificationLimit;
import nu.marginalia.language.WordPatterns;
import nu.marginalia.language.encoding.AsciiFlattener;
import nu.marginalia.util.transform_list.TransformList;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class QueryParser {

    public List<QueryToken> parse(String query) {
        List<QueryToken> basicTokens = tokenizeQuery(query);

        TransformList<QueryToken> list = new TransformList<>(basicTokens);

        list.transformEach(QueryParser::trimLiterals);
        list.transformEach(QueryParser::handleQuoteTokens);
        list.transformEachPair(QueryParser::createNegatedTerms);
        list.transformEachPair(QueryParser::createPriorityTerms);
        list.transformEach(QueryParser::handleSpecialOperations);
        list.scanAndTransform(QueryToken.LParen.class::isInstance, QueryToken.RParen.class::isInstance, QueryParser::handleAdvisoryTerms);
        list.transformEach(QueryParser::normalizeDomainName);

        return list.getBackingList();
    }

    private static final Pattern noisePattern = Pattern.compile("[,\\s]");

    public List<QueryToken> tokenizeQuery(String rawQuery) {
        List<QueryToken> tokens = new ArrayList<>();

        String query = AsciiFlattener.flattenUnicode(rawQuery);
        query = noisePattern.matcher(query).replaceAll(" ");

        int chr = -1;
        int parenDepth = 0;
        for (int i = 0; i < query.length(); i++) {
            chr = query.charAt(i);

            if ('(' == chr) {
                parenDepth++;
                tokens.add(new QueryToken.LParen());
            }
            else if (')' == chr) {
                parenDepth--;
                tokens.add(new QueryToken.RParen());
            }
            else if ('"' == chr) {
                int end = query.indexOf('"', i+1);

                if (end == -1) {
                    end = query.length();
                }

                tokens.add(new QueryToken.Quot(query.substring(i + 1, end).toLowerCase()));

                i = end;
            }
            else if ('-' == chr) {
                tokens.add(new QueryToken.Minus());
            }
            else if ('?' == chr) {
                tokens.add(new QueryToken.QMark());
            }
            else if (!Character.isSpaceChar(chr)) {

                // search for the end of the term
                int end = i+1;
                int prevC = -1;
                int c = -1;
                for (; end < query.length(); end++) {
                    prevC = c;
                    c = query.charAt(end);

                    if (prevC == '\\')
                        continue;
                    if (c == ' ')
                        break;

                    // special case to deal with possible RPAREN token at the end,
                    // but we don't want to break if it's likely part of the search term
                    if (c == ')' && prevC != '(' && parenDepth > 0) {
                        break;
                    }
                }

                String displayStr = query.substring(i, end);
                String str = trimEscape(displayStr.toLowerCase());

                tokens.add(new QueryToken.LiteralTerm(str, displayStr));

                i = end-1;
            }
        }
        return tokens;
    }

    private String trimEscape(String str) {
        if (!str.contains("\\")) {
            return str;
        }

        StringBuilder sb = new StringBuilder(str.length());
        for (int j = 0; j < str.length(); j++) {
            char c = str.charAt(j);
            if (c == '\\') {
                if (j + 1 < str.length()) {
                    sb.append(str.charAt(j + 1));
                    j++;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void normalizeDomainName(TransformList<QueryToken>.Entity entity) {
        var t = entity.value();

        if (!(t instanceof QueryToken.LiteralTerm))
            return;

        if (t.str().startsWith("site:")) {
            entity.replace(new QueryToken.LiteralTerm(t.str().toLowerCase(), t.displayStr()));
        }

    }

    private static void handleQuoteTokens(TransformList<QueryToken>.Entity entity) {
        var t = entity.value();

        if (!(t instanceof QueryToken.Quot)) {
            return;
        }

        entity.replace(new QueryToken.QuotTerm(
                t.str().replaceAll("\\s+", WordPatterns.WORD_TOKEN_JOINER),
                t.displayStr()));
    }

    private static void trimLiterals(TransformList<QueryToken>.Entity entity) {
        var t = entity.value();

        if (!(t instanceof QueryToken.LiteralTerm lt))
            return;

        String str = lt.str();
        if (str.isBlank())
            return;

        // Remove trailing punctuation
        int lastChar = str.charAt(str.length() - 1);
        if (":.,!?$'".indexOf(lastChar) >= 0) {
            str = str.substring(0, str.length() - 1);
            entity.replace(new QueryToken.LiteralTerm(str, lt.displayStr()));
        }

        // Remove term elements that aren't indexed by the search engine
        if (str.endsWith("'s")) {
            str = str.substring(0, str.length() - 2);
            entity.replace(new QueryToken.LiteralTerm(str, lt.displayStr()));
        }
        if (str.endsWith("()")) {
            str = str.substring(0, str.length() - 2);
            entity.replace(new QueryToken.LiteralTerm(str, lt.displayStr()));
        }

        while (str.startsWith("$") || str.startsWith("_")) {
            str = str.substring(1);
            entity.replace(new QueryToken.LiteralTerm(str, lt.displayStr()));
        }

        if (entity.isBlank()) {
            entity.remove();
        }
    }

    private static void createNegatedTerms(TransformList<QueryToken>.Entity first, TransformList<QueryToken>.Entity second) {
        var t = first.value();
        var tn = second.value();

        if (!(t instanceof QueryToken.Minus))
            return;
        if (!(tn instanceof QueryToken.LiteralTerm) && !(tn instanceof QueryToken.AdviceTerm))
            return;

        first.remove();

        second.replace(new QueryToken.ExcludeTerm(tn.str(), "-" + tn.displayStr()));
    }

    private static void createPriorityTerms(TransformList<QueryToken>.Entity first, TransformList<QueryToken>.Entity second) {
        var t = first.value();
        var tn = second.value();

        if (!(t instanceof QueryToken.QMark))
            return;
        if (!(tn instanceof QueryToken.LiteralTerm) && !(tn instanceof QueryToken.AdviceTerm))
            return;

        var replacement = new QueryToken.PriorityTerm(tn.str(), "?" + tn.displayStr());

        first.remove();
        second.replace(replacement);
    }

    private static void handleSpecialOperations(TransformList<QueryToken>.Entity entity) {
        var t = entity.value();
        if (!(t instanceof QueryToken.LiteralTerm)) {
            return;
        }

        String str = t.str();

        if (str.startsWith("q") && str.matches("q[=><]\\d+")) {
            var limit = parseSpecificationLimit(str.substring(1));
            entity.replace(new QueryToken.QualityTerm(limit, str));
        } else if (str.startsWith("near:")) {
            entity.replace(new QueryToken.NearTerm(str.substring(5)));
        } else if (str.startsWith("year") && str.matches("year[=><]\\d{4}")) {
            var limit = parseSpecificationLimit(str.substring(4));
            entity.replace(new QueryToken.YearTerm(limit, str));
        } else if (str.startsWith("size") && str.matches("size[=><]\\d+")) {
            var limit = parseSpecificationLimit(str.substring(4));
            entity.replace(new QueryToken.SizeTerm(limit, str));
        } else if (str.startsWith("rank") && str.matches("rank[=><]\\d+")) {
            var limit = parseSpecificationLimit(str.substring(4));
            entity.replace(new QueryToken.RankTerm(limit, str));
        } else if (str.startsWith("qs=")) {
            entity.replace(new QueryToken.QsTerm(str.substring(3)));
        } else if (str.startsWith("site:")
                || str.startsWith("format:")
                || str.startsWith("file:")
                || str.startsWith("tld:")
                || str.startsWith("ip:")
                || str.startsWith("as:")
                || str.startsWith("asn:")
                || str.startsWith("generator:")
        )
        {
            entity.replace(new QueryToken.AdviceTerm(str, t.displayStr()));
        }

    }

    private static SpecificationLimit parseSpecificationLimit(String str) {
        int startChar = str.charAt(0);

        int val = Integer.parseInt(str.substring(1));
        if (startChar == '=') {
            return SpecificationLimit.equals(val);
        } else if (startChar == '<') {
            return SpecificationLimit.lessThan(val);
        } else if (startChar == '>') {
            return SpecificationLimit.greaterThan(val);
        } else {
            return SpecificationLimit.none();
        }
    }

    private static void handleAdvisoryTerms(TransformList<QueryToken>.Entity entity) {
        var t = entity.value();
        if (t instanceof QueryToken.LParen) {
            entity.remove();
        } else if (t instanceof QueryToken.RParen) {
            entity.remove();
        } else if (t instanceof QueryToken.LiteralTerm) {
            entity.replace(new QueryToken.AdviceTerm(t.str(), "(" + t.displayStr() + ")"));
        }
    }

}

