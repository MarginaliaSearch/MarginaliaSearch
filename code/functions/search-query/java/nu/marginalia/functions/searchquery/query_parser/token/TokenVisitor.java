package nu.marginalia.functions.searchquery.query_parser.token;

public interface TokenVisitor {
    void onLiteralTerm(Token token);
    void onQuotTerm(Token token);
    void onExcludeTerm(Token token);
    void onPriorityTerm(Token token);
    void onAdviceTerm(Token token);
    void onYearTerm(Token token);
    void onSizeTerm(Token token);
    void onRankTerm(Token token);
    void onDomainCountTerm(Token token);
    void onQualityTerm(Token token);
    void onQsTerm(Token token);
}
