package nu.marginalia.wmsa.renderer.mustache;

import com.github.jknack.handlebars.*;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import lombok.SneakyThrows;
import nu.marginalia.gemini.gmi.GemtextDocument;
import nu.marginalia.wmsa.memex.model.MemexNodeUrl;
import nu.marginalia.wmsa.memex.model.render.MemexRendererIndexModel;
import nu.marginalia.wmsa.memex.model.render.MemexRendererViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MustacheRenderer<T> {
    Template template;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    MustacheRenderer(String templateFile) throws IOException {

        TemplateLoader loader = new ClassPathTemplateLoader();
        loader.setPrefix("/templates");
        loader.setSuffix(".hdb");

        var handlebars = new Handlebars(loader);
        handlebars.registerHelpers(ConditionalHelpers.class);
        handlebars.registerHelpers(new GeminiHelpers());
        handlebars.registerHelper("md", new MarkdownHelper());
        handlebars.registerHelper("gen-url", this::genereraUrl);
        handlebars.registerHelper("gen-thread-url", this::genereraTradUrl);
        handlebars.registerHelper("gen-author-url", this::generereraAuthorUrl);



        try {
            template = handlebars.compile(templateFile);
        }
        catch (FileNotFoundException ex) {
            logger.error("Kunde inte ladda template " + templateFile, ex);
            System.exit(2);
        }
        catch (HandlebarsException ex) {
            logger.error("Kunde inte instantiera mall " + templateFile, ex);
            System.exit(2);
        }
    }

    public static final class GeminiHelpers {

        public CharSequence pragma(Options options) throws IOException {
            var model = options.context.model();
            GemtextDocument doc;
            if (model instanceof MemexRendererIndexModel) {
                doc = ((MemexRendererIndexModel) model).getDocument("index.gmi");
            }
            else if (model instanceof MemexRendererViewModel) {
                doc = ((MemexRendererViewModel)model).baseDoc;
            }
            else {
                doc = null;
            }

            if (doc != null && doc.getPragmas().contains((String) options.param(0))) {
                return options.fn(options.context);
            }
            return null;
        }
        public CharSequence amgarp(Options options) throws IOException {
            var model = options.context.model();
            GemtextDocument doc;
            if (model instanceof MemexRendererIndexModel) {
                doc = ((MemexRendererIndexModel) model).getDocument("index.gmi");
            }
            else if (model instanceof MemexRendererViewModel) {
                doc = ((MemexRendererViewModel)model).baseDoc;
            }
            else {
                doc = null;
            }

            if (doc == null || !doc.getPragmas().contains((String) options.param(0))) {
                return options.fn(options.context);
            }
            return null;
        }

        public CharSequence topbar(MemexNodeUrl url, Options options) throws IOException {
            var path = url.asRelativePath();
            LinkedList<Path> nodes = new LinkedList<>();

            for (Path p = path; p != null; p = p.getParent()) {
                nodes.addFirst(p);
            }
            StringBuilder sb = new StringBuilder();
            for (var p : nodes) {
                String name = p.toFile().getName();
                String type = "dir";
                if ("".equals(name)) {
                    name = "marginalia";
                    type = "root";
                }
                if (p.equals(path) && name.contains(".")) {
                    type = "file";
                }
                Context newCtx = Context.newBlockParamContext(options.context,
                        List.of("url", "name", "type"),
                        List.of(p, name, type)
                        );
                sb.append(options.fn(newCtx));
            }
            return sb.toString();
        }
    }

    @SneakyThrows
    public String render(T model) {
        return template.apply(model);
    }

    @SneakyThrows
    public <T2> String render(T model, String name, List<T2> children) {
        Context ctx = Context.newBuilder(model).combine(name, children).build();

        return template.apply(ctx);
    }

    @SneakyThrows
    public <T2> String render(T model, Map<String, ?> children) {
        Context ctx = Context.newBuilder(model).combine(children).build();
        return template.apply(ctx);
    }

    private Object genereraUrl(Object context, Options options) {
        if (null != context) {
            return context.toString().toLowerCase() + ".html";
        } else {
            logger.error("Kunde inte generera URL, blockParams {}", options.blockParams);
            return "";
        }
    }
    private Object genereraTradUrl(Object context, Options options) {
        if (null != context) {
            return context.toString().toLowerCase() + "/view.html";
        } else {
            logger.error("Kunde inte generera URL, blockParams {}", options.blockParams);
            return "";
        }
    }
    private Object generereraAuthorUrl(Object context, Options options) {
        if (null != context) {
            return "u_" + context.toString().toLowerCase() + ".html";
        } else {
            logger.error("Kunde inte generera URL, blockParams {}", options.blockParams);
            return "";
        }
    }
}
