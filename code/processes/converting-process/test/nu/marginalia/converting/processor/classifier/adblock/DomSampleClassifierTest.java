package nu.marginalia.converting.processor.classifier.adblock;

import nu.marginalia.domclassifier.DomSampleClassifier;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

class DomSampleClassifierTest {

    @Test
    public void  testLoadSpecs() throws ParserConfigurationException, IOException, SAXException {
        new DomSampleClassifier();
    }
}