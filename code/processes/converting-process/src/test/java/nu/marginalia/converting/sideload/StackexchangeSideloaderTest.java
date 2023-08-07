package nu.marginalia.converting.sideload;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Path;

class StackexchangeSideloaderTest {
    @Test
    public void test7zFile() throws IOException, XMLStreamException {
        var stackExchangeReader = new StackExchange7zReader(Path.of("/mnt/storage/stackexchange/scifi.meta.stackexchange.com.7z"));

        System.out.println(stackExchangeReader.getIds());

        var iter = stackExchangeReader.postIterator();
        while (iter.hasNext()) {
            System.out.println(iter.next());
        }
    }
}