package nu.marginalia.renderer.config;

import com.github.jknack.handlebars.Handlebars;

/** Configure handlebars rendering by injecting helper methods
 * into the setup process */
public interface HandlebarsConfigurator {

    /** Set up helpers for this handlebars instance */
    void configure(Handlebars handlebars);
}
