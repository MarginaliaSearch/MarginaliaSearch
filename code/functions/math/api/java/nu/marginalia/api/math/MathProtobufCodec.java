package nu.marginalia.api.math;

import nu.marginalia.api.math.model.DictionaryEntry;
import nu.marginalia.api.math.model.DictionaryResponse;

import java.util.List;

public class MathProtobufCodec {

    public static class DictionaryLookup {
        public static RpcDictionaryLookupRequest createRequest(String word) {
            return RpcDictionaryLookupRequest.newBuilder()
                    .setWord(word)
                    .build();
        }
        public static DictionaryResponse convertResponse(RpcDictionaryLookupResponse rsp) {
            return new DictionaryResponse(
                    rsp.getWord(),
                    rsp.getEntriesList().stream().map(DictionaryLookup::convertResponseEntry).toList()
            );
        }

        private static DictionaryEntry convertResponseEntry(RpcDictionaryEntry e) {
            return new DictionaryEntry(e.getType(), e.getWord(), e.getDefinition());
        }
    }

    public static class SpellCheck {
        public static RpcSpellCheckRequest createRequest(String text) {
            return RpcSpellCheckRequest.newBuilder()
                    .setText(text)
                    .build();
        }

        public static List<String> convertResponse(RpcSpellCheckResponse rsp) {
            return rsp.getSuggestionsList();
        }
    }

    public static class UnitConversion {
        public static RpcUnitConversionRequest createRequest(String from, String to, String unit) {
            return RpcUnitConversionRequest.newBuilder()
                    .setFrom(from)
                    .setTo(to)
                    .setUnit(unit)
                    .build();
        }

        public static String convertResponse(RpcUnitConversionResponse rsp) {
            return rsp.getResult();
        }
    }

    public static class EvalMath {
        public static RpcEvalMathRequest createRequest(String expression) {
            return RpcEvalMathRequest.newBuilder()
                    .setExpression(expression)
                    .build();
        }

        public static String convertResponse(RpcEvalMathResponse rsp) {
            return rsp.getResult();
        }
    }

}
