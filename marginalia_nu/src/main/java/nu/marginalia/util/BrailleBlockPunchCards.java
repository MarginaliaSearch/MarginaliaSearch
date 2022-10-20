package nu.marginalia.util;

public class BrailleBlockPunchCards {

    public static String printBits(int val, int bits) {
        StringBuilder builder = new StringBuilder();

        for (int b = 0; b < bits; b+=8, val>>>=8) {
            builder.append((char)('\u2800'+bin2brail(val)));
        }

        return builder.toString();
    }

    /* The braille block in unicode U2800 is neat because it contains
     * 8 "bits", but for historical reasons, they're addressed in a bit
     * of an awkward way. Braille used to be a 2x6 grid, but it was extended
     * to 2x8.
     *
     * It's addressed as follows
     *
     * 0 3
     * 1 4
     * 2 5
     * 6 7 <-- extended braille
     *
     *
     * We want to use it as a dot matrix to represent bits. To do that we need
     * to do this transformation:
     *
     *  0  1  2  3  4  5  6  7   native order bits
     *  |  |  |   \ _\__\/   |
     *  |  |  |   / \  \ \   |
     *  0  1  2  6  3  4  5  7   braille order bits
     *
     * 01 02 04 08 10 20 40 80
     * 01+02+04            +80 : &0x87
     *          << 10+20+40    : &0x70, <<1
     *          08 >> >> >>    : &0x08, >>3
     *
     * Or in other words we do
     *     (v & 0x87)
     *  | ((v & 0x70) >> 1)
     *  | ((v & 0x08) << 3)
     *
     * Thanks for coming to my TED talk.
     */

    private static char bin2brail(int v) {
        return (char)((v & 0x87) | ((v & 0x70) >> 1) | ((v & 0x08) << 3));
    }
}
