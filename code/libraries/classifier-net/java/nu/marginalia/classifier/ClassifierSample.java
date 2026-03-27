package nu.marginalia.classifier;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public sealed interface ClassifierSample {

    double y0();
    int[] x();
    @Nullable
    double[] act();

    default boolean isEmpty() {
        return x().length == 0;
    }

    static double[] activationFromCount(int[] counts) {
        double[] activationNormalized = new double[counts.length];

        for (int i = 0; i < counts.length; i++) {
            // Gives input activation values of

            // cnt:  1        2       3       4       5       6       7
            // act:  0.29     0.5     0.65    0.75    0.82    0.88    0.91

            activationNormalized[i] = 1 - Math.pow(2, -counts[i]/2.);
        }

        return activationNormalized;
    }

    public static ClassifierSample ofBinary(int x[], double y0) {
        return new BinaryClassifierSample(x, y0);
    }

    public static ClassifierSample ofCounted(int x[], int[] counts, double y0) {
        return new CountedClassifierSample(x, activationFromCount(counts), y0);
    }

    public record BinaryClassifierSample(int[] x, double y0) implements ClassifierSample {

        public String toString() {
            return String.format("Features: %s, label: %2.2f", Arrays.toString(x), y0);
        }

        @Override
        public double[] act() {
            return null;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(x);
        }
    }

    public record CountedClassifierSample(int[] x, double[] act, double y0) implements ClassifierSample {

        public String toString() {
            return String.format("Features: %s, Activation: %s, label: %2.2f", Arrays.toString(x), Arrays.toString(act), y0);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(x);
        }
    }


}
