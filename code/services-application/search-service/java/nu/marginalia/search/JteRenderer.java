package nu.marginalia.search;

import gg.jte.CodeResolver;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import gg.jte.resolve.ResourceCodeResolver;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class JteRenderer {
    private final CodeResolver codeResolver = new ResourceCodeResolver("jte");
    private final TemplateEngine templateEngine = TemplateEngine.create(codeResolver, ContentType.Html);

    public String render(String template, Object model) {
        StringOutput output = new StringOutput();
        templateEngine.render(template, model, output);
        return output.toString();
    }

    public String render(String template, Map<String, Object> models) {
        StringOutput output = new StringOutput();
        templateEngine.render(template, models, output);
        return output.toString();
    }

}
