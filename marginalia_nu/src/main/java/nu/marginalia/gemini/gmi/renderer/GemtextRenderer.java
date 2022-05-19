package nu.marginalia.gemini.gmi.renderer;

import nu.marginalia.gemini.gmi.line.*;

import java.util.function.Function;

public class GemtextRenderer implements GemtextLineVisitor<String> {

    private final Function<GemtextHeading, String> headingConverter;
    private final Function<GemtextLink, String> linkConverter;
    private final Function<GemtextList, String> listConverter;
    private final Function<GemtextPreformat, String> preformatConverter;
    private final Function<GemtextQuote, String> quoteConverter;
    private final Function<GemtextText, String> textConverter;
    private final Function<GemtextAside, String> asideConverter;
    private final Function<GemtextTask, String> taskConverter;
    private final Function<GemtextTextLiteral, String> literalConverter;
    private final Function<GemtextPragma, String> pragmaConverter;

    public GemtextRenderer(Function<GemtextHeading, String> headingConverter,
                           Function<GemtextLink, String> linkConverter,
                           Function<GemtextList, String> listConverter,
                           Function<GemtextPreformat, String> preformatConverter,
                           Function<GemtextQuote, String> quoteConverter,
                           Function<GemtextText, String> textConverter,
                           Function<GemtextAside, String> asideConverter,
                           Function<GemtextTask, String> taskConverter,
                           Function<GemtextTextLiteral, String> literalConverter,
                           Function<GemtextPragma, String> pragmaConverter
                           ) {
        this.headingConverter = headingConverter;
        this.linkConverter = linkConverter;
        this.listConverter = listConverter;
        this.preformatConverter = preformatConverter;
        this.quoteConverter = quoteConverter;
        this.textConverter = textConverter;
        this.asideConverter = asideConverter;
        this.taskConverter = taskConverter;
        this.literalConverter = literalConverter;
        this.pragmaConverter = pragmaConverter;
    }


    public String renderLine(AbstractGemtextLine line) {
        return line.visit(this);
    }

    @Override
    public String visit(GemtextHeading g) {
        return headingConverter.apply(g);
    }

    @Override
    public String visit(GemtextLink g) {
        return linkConverter.apply(g);
    }

    @Override
    public String visit(GemtextList g) {
        return listConverter.apply(g);
    }

    @Override
    public String visit(GemtextPreformat g) {
        return preformatConverter.apply(g);
    }

    @Override
    public String visit(GemtextQuote g) {
        return quoteConverter.apply(g);
    }

    @Override
    public String visit(GemtextText g) {
        return textConverter.apply(g);
    }

    @Override
    public String visit(GemtextTextLiteral g) {
        return literalConverter.apply(g);
    }

    @Override
    public String visit(GemtextAside g) { return asideConverter.apply(g); }

    @Override
    public String visit(GemtextTask g) { return taskConverter.apply(g); }

    @Override
    public String visit(GemtextPragma g) { return pragmaConverter.apply(g); }
}
