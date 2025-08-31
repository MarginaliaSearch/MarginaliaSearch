package nu.marginalia.converting.sideload.encyclopedia;

import nu.marginalia.encyclopedia.EncyclopediaConverter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Tag("slow")
class EncyclopediaMarginaliaNuSideloaderConvertTest {

    @Test
    public void testFullConvert() throws IOException {
        Path inputFile = Path.of("/home/vlofgren/Work/wikipedia_en_100_2025-08.zim");
        if (!Files.exists(inputFile))
            return;

        Path outputFile = Files.createTempFile("encyclopedia", ".db");
        try {
            EncyclopediaConverter.convert(inputFile, outputFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            Files.delete(outputFile);
        }
    }

}