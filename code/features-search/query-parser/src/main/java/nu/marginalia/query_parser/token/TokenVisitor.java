package nu.marginalia.query_parser.token;

public interface TokenVisitor {
    void onLiteralTerm(Token token);
    void onQuotTerm(Token token);
    void onExcludeTerm(Token token);
    void onPriorityTerm(Token token);
    void onAdviceTerm(Token token);
    void onNearTerm(Token token);

    void onYearTerm(Token token);
    void onSizeTerm(Token token);
    void onRankTerm(Token token);
    void onQualityTerm(Token token);
    void onQsTerm(Token token);
}
