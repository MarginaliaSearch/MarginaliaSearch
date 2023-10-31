package nu.marginalia.control;

import jakarta.inject.Inject;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import spark.ResponseTransformer;

import java.io.IOException;
import java.util.Map;

public class RedirectControl {
    private final MustacheRenderer<Object> renderer;

    @Inject
    public RedirectControl(RendererFactory rendererFactory) throws IOException {
        renderer = rendererFactory.renderer("control/redirect-ok");
    }

    public ResponseTransformer renderRedirectAcknowledgement(String message, String redirectUrl) {
        return rsp -> justRender(message, redirectUrl);
    }

    public String justRender(String message, String redirectUrl) {
        return renderer.render(
                Map.of(
                        "message", message,
                        "redirectUrl", redirectUrl
                )
        );
    }
}
