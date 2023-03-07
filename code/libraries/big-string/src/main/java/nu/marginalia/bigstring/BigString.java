package nu.marginalia.bigstring;

public interface BigString {
    static BigString encode(String stringValue) {
        if (stringValue.length() > 64) {
            return new CompressedBigString(stringValue);
        }
        else {
            return new PlainBigString(stringValue);
        }
    }
    String decode();

    byte[] getBytes();

    int length();
}
