package nu.marginalia.bigstring;

public interface BigString {

    boolean disableBigString = Boolean.getBoolean("bigstring.disabled");

    static BigString encode(String stringValue) {
        if (!disableBigString && stringValue.length() > 64) {
            return new CompressedBigString(stringValue);
        }
        else {
            return new PlainBigString(stringValue);
        }
    }
    String decode();

    int length();
}
