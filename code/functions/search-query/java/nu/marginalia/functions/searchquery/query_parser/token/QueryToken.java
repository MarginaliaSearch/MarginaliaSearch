package nu.marginalia.functions.searchquery.query_parser.token;


public sealed interface QueryToken {
    String str();
    String displayStr();

    record LiteralTerm(String str, String displayStr) implements QueryToken {}
    record QuotTerm(String str, String displayStr) implements QueryToken {}
    record ExcludeTerm(String str, String displayStr) implements QueryToken {}
    record AdviceTerm(String str, String displayStr) implements QueryToken {}
    record PriorityTerm(String str, String displayStr) implements QueryToken {}

    record QualityTerm(String str) implements QueryToken {
        public String displayStr() {
            return "q" + str;
        }
    }
    record YearTerm(String str) implements QueryToken {
        public String displayStr() {
            return "year" + str;
        }
    }
    record SizeTerm(String str) implements QueryToken {
        public String displayStr() {
            return "size" + str;
        }
    }
    record RankTerm(String str) implements QueryToken {
        public String displayStr() {
            return "rank" + str;
        }
    }
    record NearTerm(String str) implements QueryToken {
        public String displayStr() {
            return "near:" + str;
        }
    }

    record QsTerm(String str) implements QueryToken {
        public String displayStr() {
            return "qs" + str;
        }
    }

    record Quot(String str) implements QueryToken {
        public String displayStr() {
            return "\"" + str + "\"";
        }
    }
    record Minus() implements QueryToken {
        public String str() {
            return "-";
        }
        public String displayStr() {
            return "-";
        }
    }
    record QMark() implements QueryToken {
        public String str() {
            return "?";
        }
        public String displayStr() {
            return "?";
        }
    }
    record LParen() implements QueryToken {
        public String str() {
            return "(";
        }
        public String displayStr() {
            return "(";
        }
    }
    record RParen() implements QueryToken {
        public String str() {
            return ")";
        }
        public String displayStr() {
            return ")";
        }
    }

    record Ignore(String str, String displayStr) implements QueryToken {}

}
