package nu.marginalia.wmsa.edge.search.model;

import java.util.TreeMap;

public class EdgeSearchRankingSymbols {

    private static final TreeMap<Double, RankingSymbol> symbols;
    static {
        symbols = new TreeMap<>();
        symbols.put(1.0, new RankingSymbol("Ⓐ", "Fits search terms very well"));
        symbols.put(3.0, new RankingSymbol("Ⓑ", "Fits search terms decently"));
        symbols.put(4.0, new RankingSymbol("Ⓒ", "Could fit search terms"));
        symbols.put(5.0, new RankingSymbol("Ⓓ", "Poor fit for search terms, grasping at straws"));
        symbols.put(100., new RankingSymbol("Ⓕ", "Poor fit for search terms, grasping at straws"));
    }

    public static String getRankingSymbol(double termScore) {
        return forScore(termScore).symbol;
    }
    public static String getRankingSymbolDescription(double termScore) {
        return forScore(termScore).description;
    }

    private static RankingSymbol forScore(double score) {
        var e = symbols.ceilingEntry(score);
        if (e == null) {
            e = symbols.lastEntry();
        }
        return e.getValue();
    }

    private record RankingSymbol(String symbol, String description) {
    }
}
