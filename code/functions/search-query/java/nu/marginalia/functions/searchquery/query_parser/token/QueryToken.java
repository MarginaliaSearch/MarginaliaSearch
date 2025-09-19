package nu.marginalia.functions.searchquery.query_parser.token;


import nu.marginalia.api.searchquery.model.query.SpecificationLimit;

public sealed interface QueryToken {
    String str();
    String displayStr();

    record LiteralTerm(String str, String displayStr) implements QueryToken {}
    record QuotTerm(String str, String displayStr) implements QueryToken {}
    record ExcludeTerm(String str, String displayStr) implements QueryToken {}
    record AdviceTerm(String str, String displayStr) implements QueryToken {}
    record PriorityTerm(String str, String displayStr) implements QueryToken {}
    record LangTerm(String str, String displayStr) implements QueryToken {}

    record QualityTerm(SpecificationLimit limit, String displayStr) implements QueryToken {
        public String str() { return displayStr; }

    }
    record YearTerm(SpecificationLimit limit, String displayStr) implements QueryToken {
        public String str() { return displayStr; }
    }
    record SizeTerm(SpecificationLimit limit, String displayStr) implements QueryToken {
        public String str() { return displayStr; }
    }
    record RankTerm(SpecificationLimit limit, String displayStr) implements QueryToken {
        public String str() { return displayStr; }
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
