package nu.marginalia.bigstring;

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
    public int length() {
        return value.length();
    }
}
