package nu.marginalia.converting.processor.logic.dom;

import nu.marginalia.dom.MeasureLengthVisitor;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MeasureLengthVisitorTest {

    @Test
    public void testMeasureLength() {
        var mlv = new MeasureLengthVisitor();
        Jsoup.parse("""
                <p>  hello world! 
                  <span> neat! </span>
                <p>
                """).traverse(mlv);
        assertEquals(15, mlv.length);
    }
}