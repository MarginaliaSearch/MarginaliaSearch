package nu.marginalia.functions.searchquery.query_parser.token;

import java.util.function.Predicate;

public enum TokenType implements Predicate<Token> {
    TERM,


    LITERAL_TERM,
    QUOT_TERM,
    EXCLUDE_TERM,
    ADVICE_TERM,
    PRIORTY_TERM,

    QUALITY_TERM,
    YEAR_TERM,
    SIZE_TERM,
    RANK_TERM,
    NEAR_TERM,

    QS_TERM,

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
