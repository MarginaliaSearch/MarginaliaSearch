package nu.marginalia.control;

import spark.ResponseTransformer;

public class HtmlRedirect implements ResponseTransformer {
    private final String html;

    /** Because Spark doesn't have a redirect method that works with relative URLs
     * (without explicitly providing the external address),we use HTML and let the
     * browser resolve the relative redirect instead */
    public HtmlRedirect(String destination) {
        this.html = """
                    <?doctype html>
                    <html><head><meta http-equiv="refresh" content="0;URL='%s'" /></head></html>
                    """.formatted(destination);
    }

    @Override
    public String render(Object any) throws Exception {
        return html;
    }
}
