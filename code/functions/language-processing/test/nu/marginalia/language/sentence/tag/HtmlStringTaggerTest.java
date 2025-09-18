package nu.marginalia.language.sentence.tag;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class HtmlStringTaggerTest {
    @Test
    public void test() {
        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                <title>T Example</title>
                </head>
                <body>
                <h1>H1 Example</h1>
                <p>This is an example.</p>
                <p>Here is more text.</p>
                <p>And more text <a href="#">with a link</a> and more text.</p>
                <code>#include &lt;stdlib.h&gt;</code>
                <h3>Good bye</h3>
                </body>
                """;
        var visitor = new HtmlStringTagger();
        Jsoup.parse(html).traverse(visitor);

        visitor.getOutput().forEach(ts -> System.out.println(ts.string() + " " + ts.tags()));
    }
}