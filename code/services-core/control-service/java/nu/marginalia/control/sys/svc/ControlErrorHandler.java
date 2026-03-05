package nu.marginalia.control.sys.svc;

import com.google.inject.Inject;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.MediaType;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.ControlValidationError;

import java.io.IOException;
import java.util.Map;

public class ControlErrorHandler {
    private final ControlRendererFactory.Renderer renderer;

    @Inject
    public ControlErrorHandler(ControlRendererFactory rendererFactory) throws IOException {
        this.renderer = rendererFactory.renderer("control/error");
    }

    public String render(ControlValidationError error) {
        return renderer.render(
                Map.of(
                "title", error.title,
                "messageLong", error.messageLong,
                "redirect", error.redirect
                )
        );
    }

    public void register(Jooby jooby) {
        jooby.error(ControlValidationError.class, (ctx, cause, code) -> {
            ctx.setResponseType(MediaType.html);
            ctx.send(render((ControlValidationError) cause));
        });
    }
}
