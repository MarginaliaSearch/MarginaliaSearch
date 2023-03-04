package nu.marginalia.wmsa.renderer.mustache;

import com.github.jknack.handlebars.*;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
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
        handlebars.registerHelper("md", new MarkdownHelper());

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

}
