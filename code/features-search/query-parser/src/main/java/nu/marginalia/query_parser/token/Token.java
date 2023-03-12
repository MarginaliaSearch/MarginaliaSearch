package nu.marginalia.query_parser.token;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.With;

@ToString
@EqualsAndHashCode
@With
public class Token {
    public TokenType type;
    public String str;
    public final String displayStr;

    public Token(TokenType type, String str, String displayStr) {
        this.type = type;
        this.str = str;
        this.displayStr = safeString(displayStr);
    }


    public Token(TokenType type, String str) {
        this.type = type;
        this.str = str;
        this.displayStr = safeString(str);
    }

    private static String safeString(String s) {
        return s.replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;");
    }
}
