package nu.marginalia.renderer;

import com.github.jknack.handlebars.*;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import nu.marginalia.renderer.config.HandlebarsConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class MustacheRenderer<T> {
    private final Template template;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    MustacheRenderer(HandlebarsConfigurator configurator, String templateFile) throws IOException {

        TemplateLoader loader = new ClassPathTemplateLoader();
        loader.setPrefix("/templates");
        loader.setSuffix(".hdb");

        var handlebars = new Handlebars(loader);

        handlebars.registerHelpers(ConditionalHelpers.class);
        handlebars.registerHelper("md", new MarkdownHelper());

        configurator.configure(handlebars);

        try {
            template = handlebars.compile(templateFile);
            logger.info("Loaded template " + templateFile);
        }
        catch (FileNotFoundException ex) {
            throw new RenderingException("Could not find template " + templateFile);
        }
        catch (HandlebarsException ex) {
            throw new RenderingException("Failed to load template " + templateFile, ex);
        }
    }

    public String render(T model) {
        try {
            return template.apply(model);
        }
        catch (IOException ex) {
            throw new RuntimeException("Failed to render template", ex);
        }
    }

    public <T2> String render(T model, String name, List<T2> children) {
        Context ctx = Context.newBuilder(model).combine(name, children).build();

        try {
            return template.apply(ctx);
        }
        catch (IOException ex) {
            throw new RuntimeException("Failed to render template", ex);
        }
    }

    public String render(T model, Map<String, ?> children) {
        Context ctx = Context.newBuilder(model).combine(children).build();

        try {
            return template.apply(ctx);
        }
        catch (IOException ex) {
            throw new RuntimeException("Failed to render template", ex);
        }
    }

}
