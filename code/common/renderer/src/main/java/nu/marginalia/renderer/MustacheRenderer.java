package nu.marginalia.renderer;

import com.github.jknack.handlebars.*;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import lombok.SneakyThrows;
import nu.marginalia.renderer.config.HandlebarsConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Response;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
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

    @SneakyThrows
    public String render(T model) {
        return template.apply(model);
    }

    private Writer getWriter(Response response) throws IOException {

        // response.raw() has a getWriter() method that fits here, but this is a trap, as subsequent
        // calls to response.raw().getOutputStream() will fail with an IllegalStateException; and we
        // have internal code that does this.

        return new OutputStreamWriter(response.raw().getOutputStream());
    }

    @SneakyThrows
    public Object renderInto(Response response, T model) {

        template.apply(model, getWriter(response));

        return "";
    }

    @SneakyThrows
    public <T2> String render(T model, String name, List<T2> children) {
        Context ctx = Context.newBuilder(model).combine(name, children).build();

        return template.apply(ctx);
    }

    @SneakyThrows
    public <T2> void renderInto(Response response, T model, String name, List<T2> children) {
        Context ctx = Context.newBuilder(model).combine(name, children).build();

        template.apply(ctx, getWriter(response));
    }

    @SneakyThrows
    public <T2> String render(T model, Map<String, ?> children) {
        Context ctx = Context.newBuilder(model).combine(children).build();
        return template.apply(ctx);
    }

    @SneakyThrows
    public <T2> void renderInto(Response response, T model, Map<String, ?> children) {
        Context ctx = Context.newBuilder(model).combine(children).build();
        template.apply(ctx, getWriter(response));
    }
}
