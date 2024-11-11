package nu.marginalia.control.sys.svc;

import com.google.inject.Inject;
import nu.marginalia.control.ControlRendererFactory;
import nu.marginalia.control.ControlValidationError;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.IOException;
import java.util.Map;

public class ControlErrorHandler {
    private final ControlRendererFactory.Renderer renderer;

    @Inject
    public ControlErrorHandler(ControlRendererFactory rendererFactory) throws IOException {
        this.renderer = rendererFactory.renderer("control/error");
    }

    public void render(ControlValidationError error, Request request, Response response) {
        String text = renderer.render(
                Map.of(
                "title", error.title,
                "messageLong", error.messageLong,
                "redirect", error.redirect
                )
        );

        response.body(text);
    }

    public void register() {
        Spark.exception(ControlValidationError.class, this::render);
    }
}
