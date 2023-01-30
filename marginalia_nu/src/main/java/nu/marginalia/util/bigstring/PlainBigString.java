package nu.marginalia.util.bigstring;

import java.nio.charset.StandardCharsets;

public class PlainBigString implements BigString {
    private final String value;

    public PlainBigString(String value) {
        this.value = value;
    }

    @Override
    public String decode() {
        return value;
    }

    @Override
    public byte[] getBytes() {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public int length() {
        return value.length();
    }
}
