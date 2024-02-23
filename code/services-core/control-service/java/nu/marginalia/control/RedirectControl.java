package nu.marginalia.control;

import com.google.inject.Inject;
import spark.ResponseTransformer;

import java.io.IOException;
import java.util.Map;

public class RedirectControl {
    private final ControlRendererFactory.Renderer renderer;

    @Inject
    public RedirectControl(ControlRendererFactory rendererFactory) throws IOException {
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
