package nu.marginalia.control.sys.svc;

import com.google.inject.Inject;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.ControlValidationError;
import io.jooby.Context;
import io.jooby.Jooby;

import java.io.IOException;
import java.util.Map;

public class ControlErrorHandler {
    private final ControlRendererFactory.Renderer renderer;

    @Inject
    public ControlErrorHandler(ControlRendererFactory rendererFactory) throws IOException {
        this.renderer = rendererFactory.renderer("control/error");
    }

    public void render(ControlValidationError error, Context ctx) {
        String text = renderer.render(
                Map.of(
                "title", error.title,
                "messageLong", error.messageLong,
                "redirect", error.redirect
                )
        );

        ctx.setResponseCode(200).setResponseType("text/html").send(text);
    }

    public void register(Jooby jooby) {
        jooby.error(ControlValidationError.class, (ctx, cause, code) -> render((ControlValidationError) cause, ctx));
    }
}
