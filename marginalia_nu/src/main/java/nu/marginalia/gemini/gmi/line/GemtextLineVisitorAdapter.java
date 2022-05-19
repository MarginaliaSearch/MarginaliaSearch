package nu.marginalia.gemini.gmi.line;

public class GemtextLineVisitorAdapter<T> implements GemtextLineVisitor<T> {
    @Override
    public T visit(GemtextHeading g) {
        return null;
    }

    @Override
    public T visit(GemtextLink g) {
        return null;
    }

    @Override
    public T visit(GemtextList g) {
        return null;
    }

    @Override
    public T visit(GemtextPreformat g) {
        return null;
    }

    @Override
    public T visit(GemtextQuote g) {
        return null;
    }

    @Override
    public T visit(GemtextText g) {
        return null;
    }

    @Override
    public T visit(GemtextTextLiteral g) {
        return null;
    }

    @Override
    public T visit(GemtextAside g) {
        return null;
    }

    @Override
    public T visit(GemtextTask g) {
        return null;
    }

    @Override
    public T visit(GemtextPragma g) {
        return null;
    }
}
