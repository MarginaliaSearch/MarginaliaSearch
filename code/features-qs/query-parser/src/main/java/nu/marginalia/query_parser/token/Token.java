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

    public void visit(TokenVisitor visitor) {
        switch (type) {
            case QUOT_TERM: visitor.onQuotTerm(this); break;
            case EXCLUDE_TERM: visitor.onExcludeTerm(this); break;
            case PRIORTY_TERM: visitor.onPriorityTerm(this); break;
            case ADVICE_TERM: visitor.onAdviceTerm(this); break;
            case LITERAL_TERM: visitor.onLiteralTerm(this); break;

            case YEAR_TERM: visitor.onYearTerm(this); break;
            case RANK_TERM: visitor.onRankTerm(this); break;
            case SIZE_TERM: visitor.onSizeTerm(this); break;
            case QS_TERM: visitor.onQsTerm(this); break;

            case QUALITY_TERM: visitor.onQualityTerm(this); break;
        }
    }
}
