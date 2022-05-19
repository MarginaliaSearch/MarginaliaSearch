package nu.marginalia.util;

public class ByteFolder {

    public byte[] foldBytes(int p, int q) {

        int pw = bitWidth(p);
        int qw = bitWidth(q);
        int qpw = qw + pw;

        long qp = Integer.toUnsignedLong(q) << pw | Integer.toUnsignedLong(p);

        int qpwBytes = ((qpw - 1) / Byte.SIZE) + 1;

        byte[] bytes = new byte[qpwBytes + 1];
        bytes[0] = (byte) pw;
        for (int i = 1; i < bytes.length; i++) {
            bytes[i] = (byte) (qp >>> (qpwBytes - i) * Byte.SIZE & 0xff);
        }

        return bytes;
    }

    // Function such that (decodeBytes o foldBytes) = identity
    public static int[] decodeBytes(byte[] data) {
        int[] dest = new int[2];
        decodeBytes(data, data.length, dest);
        return dest;
    }

    public static void decodeBytes(byte[] data, int length, int[] dest) {
        long val = 0;

        for (int i = 1; i < length; i++) {
            val = (val << 8) | ((0xFF)&data[i]);
        }

        dest[1] = (int)(val >>> data[0]);
        dest[0] = (int)(val & ~(dest[1]<<data[0]));
    }

    private static int bitWidth(int q) {
        int v = Integer.numberOfLeadingZeros(q);
        if (v == 32) return 1;
        return 32-v;
    }

    public static String byteBits(byte[] b) {
        return byteBits(b, b.length);
    }

    public static String byteBits(byte[] b, int n) {
        StringBuilder s = new StringBuilder();
        for (int j = 0; j < n;j++) {
            if (!s.toString().isBlank()) {
                s.append(":");
            }
            for (int i = 7; i >= 0; i--) {
                s.append((b[j] & (1L << i)) > 0 ? 1 : 0);
            }
        }
        return s.toString();
    }
    public static String intBits(int v) {
        StringBuilder s = new StringBuilder();
        for (int i = 32; i >=0; i--) {
            s.append((v & (1L << i)) > 0 ? 1 : 0);
        }
        return s.toString();
    }
    public static String longBits(long v) {
        StringBuilder s = new StringBuilder();
        for (int i = 64; i >=0; i--) {
            s.append((v & (1L << i)) > 0 ? 1 : 0);
        }
        return s.toString();
    }


}
