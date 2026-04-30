package ca.rmen.porterstemmer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class PorterStemmerOracleTest {

    private static final Path WORDLIST = Path.of("/usr/share/dict/words");


    @Test
    public void test() throws IOException {
        if (!Files.isRegularFile(WORDLIST)) {
            return;
        }

        PorterStemmer current = new PorterStemmer();
        PorterStemmerOriginal oracle = new PorterStemmerOriginal();

        List<String> words = Files.readAllLines(WORDLIST);

        for (String word : words) {
            String expected = oracle.stemWord(word);
            String actual = current.stemWord(word);
            Assertions.assertEquals(expected, actual, "Mismatch for word: " + word);
        }
    }

}
