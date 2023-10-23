package nu.marginalia.renderer;

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
    private final Template template;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    MustacheRenderer(String templateFile) throws IOException {

        TemplateLoader loader = new ClassPathTemplateLoader();
        loader.setPrefix("/templates");
        loader.setSuffix(".hdb");

        var handlebars = new Handlebars(loader);
        handlebars.registerHelpers(ConditionalHelpers.class);
        handlebars.registerHelper("md", new MarkdownHelper());
        handlebars.registerHelper("readableUUID", (context, options) -> {
            if (context == null) return "";
            String instance = context.toString();
            if (instance.length() < 31) return "";

            instance = instance.replace("-", "");
            String color1 = "#"+instance.substring(0, 6);
            String color2 = "#"+instance.substring(6, 12);
            String color3 = "#"+instance.substring(12, 18);
            String color4 = "#"+instance.substring(18, 24);

            String shortName1 = instance.substring(0, 2);
            String shortName2 = instance.substring(2, 4);
            String shortName3 = instance.substring(4, 6);
            String shortName4 = instance.substring(6, 8);

            String ret = "<span title=\"%s\">".formatted(instance) +
                    "<span style=\"text-shadow: 0 0 0.2ch %s; font-family: monospace;\">%s</span>".formatted(color1, shortName1) +
                    "<span style=\"text-shadow: 0 0 0.2ch %s; font-family: monospace;\">%s</span>".formatted(color2, shortName2) +
                    "<span style=\"text-shadow: 0 0 0.2ch %s; font-family: monospace;\">%s</span>".formatted(color3, shortName3) +
                    "<span style=\"text-shadow :0 0 0.2ch %s; font-family: monospace;\">%s</span>".formatted(color4, shortName4);
            return ret;
        });

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
