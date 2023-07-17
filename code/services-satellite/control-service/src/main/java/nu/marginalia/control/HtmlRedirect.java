package nu.marginalia.control;

import spark.ResponseTransformer;

public class HtmlRedirect implements ResponseTransformer {
    private final String html;

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
