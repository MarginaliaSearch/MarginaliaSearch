package nu.marginalia.memex.gemini.gmi.line;

public interface GemtextLineVisitor<T> {
    default T take(AbstractGemtextLine line) {
        return line.visit(this);
    }

    T visit(GemtextHeading g);
    T visit(GemtextLink g);
    T visit(GemtextList g);
    T visit(GemtextPreformat g);
    T visit(GemtextQuote g);
    T visit(GemtextText g);
    T visit(GemtextTextLiteral g);
    T visit(GemtextAside g);
    T visit(GemtextTask g);
    T visit(GemtextPragma g);
}
