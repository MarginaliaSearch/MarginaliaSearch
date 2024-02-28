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

        var baseRenderer = rendererFactory.renderer(template);

        // We might want to add some sort of caching here, as this is called for every request
        // (but we can't cache the result forever, as the node list might change)

        return (context) ->
            baseRenderer.render(context,
                    Map.of("global-context",
                            Map.of(
                            "appBorder", System.getProperty("control.appBorder", "none;"),
                            "nodes", nodeConfigurationService.getAll(),
                            "hideMarginaliaApp", Boolean.getBoolean("control.hideMarginaliaApp")
                            )
                    )
            );
    }

    public interface Renderer {
        String render(Object context);
    }
}
