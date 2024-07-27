package nu.marginalia.model.idx;

import nu.marginalia.sequence.CodedSequence;

import java.util.List;

public record CodedWordSpan(byte code, CodedSequence spans) {
    public static SplitSpansList fromSplit(String codes, List<CodedSequence> spans) {
        return new SplitSpansList(codes, spans);
    }
    public static SplitSpansList split(List<CodedWordSpan> spanList) {
        return new SplitSpansList(
                spanList.stream()
                    .map(CodedWordSpan::code)
                    .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString(),
                spanList.stream()
                        .map(CodedWordSpan::spans)
                        .toList()
            );
    }

    public record SplitSpansList(String codes, List<CodedSequence> spans) {
        public List<CodedWordSpan> unite() {
            if (null == codes) {
                return List.of();
            }
            else {
                return codes.chars().mapToObj(c -> new CodedWordSpan((byte) c, spans.get(codes.indexOf(c)))).toList();
            }
        }
    }
}
