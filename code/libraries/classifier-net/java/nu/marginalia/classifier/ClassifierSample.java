package nu.marginalia.classifier;

import java.util.Arrays;

public record ClassifierSample(int[] x, double y0) {
    public String toString() {
        return String.format("Features: %s, label: %2.2f", Arrays.toString(x), y0);
    }
}
