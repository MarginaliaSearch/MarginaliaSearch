package nu.marginalia.query_parser;

import nu.marginalia.language.encoding.AsciiFlattener;
import nu.marginalia.query_parser.token.Token;
import nu.marginalia.query_parser.token.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class QueryTokenizer {
    private static final Pattern noisePattern = Pattern.compile("[,]");

    public List<Token> tokenizeQuery(String rawQuery) {
        List<Token> tokens = new ArrayList<>();

        String query = AsciiFlattener.flattenUnicode(rawQuery);
        query = noisePattern.matcher(query).replaceAll(" ");

        for (int i = 0; i < query.length(); i++) {
            int chr = query.charAt(i);

            if ('(' == chr) {
                tokens.add(new Token(TokenType.LPAREN, "(", "("));
            }
            else if (')' == chr) {
                tokens.add(new Token(TokenType.RPAREN, ")", ")"));
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


}
