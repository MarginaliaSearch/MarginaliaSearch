package nu.marginalia.integration.stackexchange.xml;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLStreamException;

class StackExchangeXmlCommentReaderTest {
    @Test
    public void testSunnyDay() throws XMLStreamException {
        String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <comments>
                  <row Id="1" PostId="1" Score="4" Text="Did I just place the first upvote?  Congrats on getting this site off the ground!" CreationDate="2016-01-12T18:47:12.573" UserId="23" ContentLicense="CC BY-SA 3.0" />
                  <row Id="2" PostId="3" Score="1" Text="I think it would be a good idea to specify what type of 3D printer you are talking about. Layer sizes can very a lot between different technologies and printing materials. On top of that, the application plays an important role. There's no general answer to the question how small a layer should be. I suggest changing this question to ask how to know what layer thickness to use and what influences that decision. That would be a canonical question an much more useful." />
                </comments>
                """;

        var iter = StackExchangeXmlCommentReader.iterator(
                new StringXmlTestEventReader(xml)
        );

        while (iter.hasNext()) {
            var comment = iter.next();
            System.out.println(comment);
        }
    }

}