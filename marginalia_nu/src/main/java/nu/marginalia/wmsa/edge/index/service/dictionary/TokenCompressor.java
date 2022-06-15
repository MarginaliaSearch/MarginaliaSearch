package nu.marginalia.wmsa.edge.index.service.dictionary;

import nu.marginalia.util.ByteFolder;
import nu.marginalia.util.dict.DictionaryHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

public class TokenCompressor {
    private final ToIntFunction<String> mapper;
    private final ByteFolder folder = new ByteFolder();
    public static final byte[] EMPTY = new byte[0];

    private static final Logger logger = LoggerFactory.getLogger(TokenCompressor.class);

    private static final Predicate<String> intPatternMatcher = Pattern.compile("[1-9][0-9]{1,8}").asMatchPredicate();


    public TokenCompressor(ToIntFunction<String> mapper) {
        this.mapper = mapper;
    }
    final char[] separators = new char[] { '_', '-', '.', '/' };
    public synchronized byte[] getWordBytes(String macroWord) {
        int ui = -1;

        for (char c : separators) {
            int ui2 = macroWord.indexOf(c);
            if (ui < 0) ui = ui2;
            else if (ui2 >= 0) ui = Math.min(ui, ui2);
        }

        if (ui <= 0 || ui >= macroWord.length()-1) {
            return getByteRepresentation(macroWord);
        }

        String car = macroWord.substring(0, ui);
        String cdr = macroWord.substring(ui+1);

        int carId = mapper.applyAsInt(car);
        int cdrId = mapper.applyAsInt(cdr);

        if (carId == DictionaryHashMap.NO_VALUE || cdrId == DictionaryHashMap.NO_VALUE) {
            return EMPTY;
        }

        return folder.foldBytes(carId, cdrId);
    }

    private byte[] getByteRepresentation(String word) {
        if (intPatternMatcher.test(word)) {
            long val = Long.parseLong(word);
            if (val < 0x100) {
                return new byte[] { 'A', (byte) (val & 0xFF)};
            }
            else if (val < 0x10000) {
                return new byte[] { 'B', (byte)((val & 0xFF00)>>8), (byte) (val & 0xFF)};
            }
            else if (val < 0x1000000) {
                return new byte[] { 'C', (byte)((val & 0xFF0000)>>12), (byte)((val & 0xFF00)>>8), (byte) (val & 0xFF)};
            }
            else if (val < 0x100000000L) {
                return new byte[] { 'D', (byte)((val & 0xFF0000)>>16), (byte)((val & 0xFF0000)>>12), (byte)((val & 0xFF00)>>8), (byte) (val & 0xFF)};
            }
        }

        var bytes = word.getBytes();
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] < 32 && (bytes[i] & 0x80) == 0) {
                logger.error("Bad byte in {} -> {} ({})", word, bytes[i], (char) bytes[i]);
                bytes[i] = '?';
            }
        }
        if (bytes.length >= Byte.MAX_VALUE) {
            return Arrays.copyOf(bytes, Byte.MAX_VALUE);
        }
        return bytes;
    }

}
