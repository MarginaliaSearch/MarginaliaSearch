package nu.marginalia.language.sentence.tag;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Assertions;
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
                <h1>H1 Example again</h1>
                <div>This is an example.</div>
                <div>Here is more text.</div>
                <div>And more text <a href="#">with a link</a> and more text.</div>
                <code>#include &lt;stdlib.h&gt;</code>
                <h3>Good bye</h3>
                </body>
                """;
        var visitor = new HtmlStringTagger();
        Jsoup.parse(html).traverse(visitor);

        var output = visitor.compactOutput();
        for (var ts : output) {
            System.out.println(ts.string() + " " + ts.tags());
        }

        var headings = output.stream().filter(e -> e.tags().contains(HtmlTag.HEADING)).toList();
        Assertions.assertEquals(2, headings.size());
        Assertions.assertEquals(" H1 Example  H1 Example again", headings.getFirst().string());
        Assertions.assertEquals(" Good bye", headings.getLast().string());
    }
}