package nu.marginalia.control;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.renderer.RendererFactory;

import java.util.Map;

/** Wrapper for the renderer factory that adds global context
 * with the nodes listing
 */
public class ControlRendererFactory {
    private final RendererFactory rendererFactory;
    private final NodeConfigurationService nodeConfigurationService;

    @Inject
    public ControlRendererFactory(RendererFactory rendererFactory,
                                  NodeConfigurationService nodeConfigurationService)
    {
        this.rendererFactory = rendererFactory;
        this.nodeConfigurationService = nodeConfigurationService;
    }

    @SneakyThrows
    public Renderer renderer(String template) {
        Map<String, Object> globalContext = Map.of(
                "nodes", nodeConfigurationService.getAll(),
                "hideMarginaliaApp", Boolean.getBoolean("control.hideMarginaliaApp")
        );
        var baseRenderer = rendererFactory.renderer(template);

        return (context) -> baseRenderer.render(context, Map.of("global-context", globalContext));
    }

    public interface Renderer {
        String render(Object context);
    }
}
