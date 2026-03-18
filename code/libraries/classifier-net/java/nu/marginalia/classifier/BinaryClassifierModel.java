package nu.marginalia.classifier;

import nu.marginalia.classifier.activation.ActivationFunction;
import nu.marginalia.classifier.activation.ReluActivationFunction;
import nu.marginalia.classifier.activation.SigmoidActivationFunction;
import nu.marginalia.slop.SlopTable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static java.lang.Math.log;

/** Single hidden layer neural network binary classifier model
 * */
public class BinaryClassifierModel {
    // Throughout the comments in this class, einstein notation is used
    // Any dangling indices are implicitly summed over
    //
    // e.g.  H[i] = a[i][j] * b[j]  is to be read as H[i] =  Σ_j a[i][j] * b[j]
    //
    // You can take the physicist out of physics, but you can't take the physics notation out of the physicist
    // https://en.wikipedia.org/wiki/Einstein_notation


    /* We have a binary classifier neural network that takes N inputs */
    public final int N_INPUTS;

    /* The model also has a hidden layer of M neurons */
    public final int M_HIDDEN;

    // We'll pass the N inputs to a hidden layer,
    // and then the hidden layer activation to an output layer


    /*
        The pre-activation of the input layer is
        z1(x)[i] = w1[i][j] * x[j] + b1[i]
     */

    // For some weights
    double[][] w1;
    // and bias vector
    double[] b1;

    // To avoid the entire network collapsing into a trivial linear algebra exercise,
    // we need an activation function at each layer to introduce a non-linearity

    // We select σ1 to be ReLU: σ1(x) = max(0,x)
    private final ActivationFunction σ1 = new ReluActivationFunction();

    /*
        The activation of the hidden layer is
        a[i] = σ1(z1[i])
     */


    /*
     *  The pre-activation of the output layer is
     *  z2(a) = w2[i] * a[i] + b2
     */

    // For output layer weights
    double[] w2;
    // And output layer bias
    double b2;

    /* The output activation, or prediction, is
        y = σ2(z2)
     */

    // We select σ2 to be the sigmoid function: σ2(x) = 1 / (1 + e^(-x))
    private final ActivationFunction σ2 = new SigmoidActivationFunction();

    public final InputActivationMode inputActivationMode;

    /* So to put it all together and make a prediction */

    public double predict(BitSet x) {
        if (inputActivationMode != InputActivationMode.BINARY)
            throw new IllegalArgumentException();

        double[] z1 = Arrays.copyOf(b1, M_HIDDEN);

        // z1(x)[i] = w1[i][j] * x[j] + b1[i]
        for (int i = 0; i < M_HIDDEN; i++) {
            for (int j = 0; j < N_INPUTS; j++) {
                if (x.get(j))
                    z1[i] += w1[i][j];
            }
        }

        // Implementation note: Here we alias the arrays to save allocations
        // z1 is garbled after the following loop
        double[] a = z1;

        // a[i] = σ1(z1[i])
        for (int i = 0; i < M_HIDDEN; i++) {
            a[i] = σ1.f(z1[i]);
        }

        // z2(a) = w2[i] * a[i] + b2
        double z2 = b2;
        for (int h = 0; h < M_HIDDEN; h++) {
            z2 += a[h] * w2[h];
        }

        // y = σ2(z2)
        return σ2.f(z2);
    }


    // For sparse input activation, this is a more efficient approach
    public double predict(int[] x) {
        if (inputActivationMode != InputActivationMode.BINARY)
            throw new IllegalArgumentException();

        double[] z1 = Arrays.copyOf(b1, M_HIDDEN);

        for (int i = 0; i < M_HIDDEN; i++) {
            for (int xi : x) {
                z1[i] += w1[i][xi];
            }
        }

        // Implementation note: Here we alias the arrays to save allocations
        // z1 is garbled after the following loop
        double[] a = z1;

        for (int i = 0; i < M_HIDDEN; i++) {
            a[i] = σ1.f(z1[i]);
        }

        double z2 = b2;
        for (int h = 0; h < M_HIDDEN; h++) {
            z2 += a[h] * w2[h];
        }

        return σ2.f(z2);
    }


    // For sparse input activation, this is a more efficient approach
    public double predict(int[] x, double[] x_act) {
        if (inputActivationMode != InputActivationMode.COUNTED)
            throw new IllegalArgumentException();

        double[] z1 = Arrays.copyOf(b1, M_HIDDEN);

        for (int i = 0; i < M_HIDDEN; i++) {

            for (int x_idx = 0; x_idx < x.length; x_idx++) {
                int xi = x[x_idx];
                double act = x_act[x_idx];

                z1[i] += w1[i][xi] * act;
            }
        }

        // Implementation note: Here we alias the arrays to save allocations
        // z1 is garbled after the following loop
        double[] a = z1;

        for (int i = 0; i < M_HIDDEN; i++) {
            a[i] = σ1.f(z1[i]);
        }

        double z2 = b2;
        for (int h = 0; h < M_HIDDEN; h++) {
            z2 += a[h] * w2[h];
        }

        return σ2.f(z2);
    }

    public double predict(ClassifierSample sample) {
        if (inputActivationMode == InputActivationMode.BINARY) {
            return predict(sample.x());
        }
        else if (inputActivationMode == InputActivationMode.COUNTED) {
            return predict(sample.x(), sample.act());
        }
        else {
            throw new IllegalStateException("Unknown input activation mode");
        }
    }


    public double trainingEpoch(List<ClassifierSample> samples, double learningRate) {
        double totalLoss = 0.;

        for (var sample : samples) {
            double[] act = sample.act();

            if (inputActivationMode == InputActivationMode.BINARY) {
                totalLoss += trainSample(sample.y0(), sample.x(), learningRate);
            }
            else if (act != null) {
                totalLoss += trainSample(sample.y0(), sample.x(), act, learningRate);
            }
            else {
                throw new IllegalArgumentException("Input activation mode is counted, but I was proved no counted sample data");
            }
        }

        return totalLoss;
    }

    public void train(List<ClassifierSample> samples, int epochs, double learningRate) {
        for (int i = 0; i < epochs; i++) {
            double loss = trainingEpoch(samples, learningRate);
            if (i > 0 && (i % 100) == 0)
                System.out.printf("Epoch %d, loss %f\n", i, loss / samples.size());
        }
    }

    /**
     *
     * @param y0 - actual value
     * @param x - list of activated inputs
     * @param lr - learning rate
     */
    public double trainSample(double y0, int[] x, double lr) {

        // To train the neural network, this is our plan:
        //
        //  1.  Make a prediction based on the input vector x
        //  2.  Calculate the loss function
        //  3.  Do gradient descent to update the weights and biases

        // Step 1, make a prediction:

        // This we've done before in predict(),
        // but it will turn out that we need to keep the preactivation z1
        // for future use, so let's not garble it this time!

        double[] z1 = Arrays.copyOf(b1, M_HIDDEN);
        for (int i = 0; i < M_HIDDEN; i++) {
            for (int xi : x) {
                z1[i] += w1[i][xi];
            }
        }

        // Hidden layer activation
        double[] a = new double[M_HIDDEN];
        for (int i = 0; i < M_HIDDEN; i++) {
            a[i] = σ1.f(z1[i]);
        }

        // Output layer preactivation
        double z2 = b2;
        for (int h = 0; h < M_HIDDEN; h++) {
            z2 += a[h] * w2[h];
        }

        // Output activation (i.e. make a prediction)
        double y = σ2.f(z2);

        // Step 2:  Evaluate the loss function

        // We're using binary cross entropy (BCE), which is a simplified
        // form of cross entropy loss that emerges since we only have
        // two mutually exclusive outputs.

        // Nomenclature note:
        // Since we have no way of representing y-hats, we're doing this shift from
        // standard nomenclature

        // y-hat -> y
        // y -> y0

        // L = -y0 * log(y) - (1 - y0) log (1 - y)

        // Keen eyed readers will spot a problem with this, which is namely that
        // log(0) is negative infinity.  To avoid this, we rock it like Socrates and
        // clamp the predicted value so that it's never quite 100% certain.

        final double eps = 1E-14;
        final double y_clamped = Math.clamp(y, eps, 1-eps);
        double L =  - y0 * log(y_clamped)
                - (1 - y0) * log(1 - y_clamped);

        // Step 3: Backpropagation via gradient descent!


        // The idea is to do a gradient descent, and adjust the weights and biases
        // like so

        // w2[i] := w2[i] - lr * ∂L/∂w2|[i]
        // b2[i] := b2[i] - lr * ∂L/∂b2|[i]
        // w1[i] := w1[i] - lr * ∂L/∂w1|[i]
        // b1[i] := b1[i] - lr * ∂L/∂b1|[i]

        // Note:  If you find the following math hard to follow (I sure did!),
        // it helps to try to derive it yourself.  Below all the notation it's
        // all just chain rules and basic algebra all the way down.

        // Side quest: Finding [∂L/∂z2 = ∂L/∂y * ∂y/∂z2]

        // Before we can get anywhere on finding the partial derivatives needed to update the
        // weights we need to find

        // The loss function is as a reminder
        //
        //      L = -y0 * log(y) - (1 - y0) log (1 - y)
        //
        // If you recall d(log(x))/dx = 1/x, and massage the maths a bit, you will find that

        //          y - y0
        // dL/dy = ---------
        //         (1-y) * y

        // Note that since dσ2(x)/dx = σ(x)(1-σ(x)),
        // given y = σ2(z2)
        //
        // dy/dz2 = (1-y) * y  (i.e. the denominator in dL/dy)

        // This falls out into an identity that given a sigmoid σ2

        //   ∂L     ∂L      ∂y
        //  ---- = ----  x -----  = y - y0
        //  ∂z2     ∂y      ∂z2

        final double dL_dz2 = y - y0;

        // Side quest over!

        // To complicate matters, we need to update the parameters in reverse,
        // since the hidden layer's derivatives will end up depending on the
        // weights in the output layer (and if we go and update w2 first, our calculations will be wrong)

        // So we're looking for

        // ∂L/∂w1
        // ∂L/∂b1

        // We keep at it with the chain rule.

        for (int i = 0; i < M_HIDDEN; i++) {

            // We know ∂L/∂z2 from the previous calculations, so that can be our starting point

            // ∂L/∂z1 = ∂L/∂z2 * ∂z2/∂z1

            // It's a given that

            // z1(x)[i] = w1[i][j] * x[j] + b1[i]
            // a[i] = σ1(z1[i])
            // z2(a) = w2[i] * a[i] + b2

            // So our mystery derivative is

            //   ∂z2      ∂z2     ∂a
            //   -----  = -----  ----
            //   ∂z1       ∂a    ∂z1

            // Both parts of RHS can be found

            // ∂z2/∂a = w2
            // ∂a/∂z1 is just a matter of our activation function

            double dLdz1 = dL_dz2 * w2[i]
                    * σ1.f_deriv(z1[i]);

            // Now we just need to find

            // ∂L/∂w1 = ∂L/∂z1 ∂z1/∂w1 = ∂L/∂z1 * x

            for (int xi : x) {
                w1[i][xi] -= lr * dLdz1;
            }

            // ∂L/∂b1 = ∂L/∂z1 ∂z1/∂b1 = ∂L/∂z1
            b1[i] -= lr * dLdz1;
        }


        // Now we can finally address the output layer

        // To adjust the output layer we need to find

        // 1. ∂L/∂w2
        // 2. ∂L/∂b2

        for (int i = 0; i < M_HIDDEN; i++) {

            // ∂L/∂w2 = ∂L/∂z2 * ∂z2 / ∂w2

            // Recall z2 = w2[i] a[i] + b2, so ∂z2/∂w2 is simply

            double dz2_dw2 = a[i];

            // Apply changes to hidden-output weights
            this.w2[i] -= lr * dL_dz2 * dz2_dw2;
        }
        // Output bias follows the same pattern, but with the conclusion that
        // ∂z2/∂b2 = 1
        b2 -= lr * dL_dz2;


        // That's it!  Weights are updated,
        // let's report back the loss

        return L;
    }



    /** Identical to the other trainSample, except we allow non-binary input activation
     *
     * @param y0 - actual value
     * @param x - list of activated inputs
     * @param x_act - activation level of each corresponding input
     * @param lr - learning rate
     */
    public double trainSample(double y0, int[] x, double[] x_act, double lr) {

        double[] z1 = Arrays.copyOf(b1, M_HIDDEN);
        for (int i = 0; i < M_HIDDEN; i++) {

            for (int x_idx = 0; x_idx < x.length; x_idx++) {
                int xi = x[x_idx];
                double act = x_act[x_idx];

                z1[i] += w1[i][xi] * act;
            }
        }

        double[] a = new double[M_HIDDEN];
        for (int i = 0; i < M_HIDDEN; i++) {
            a[i] = σ1.f(z1[i]);
        }

        double z2 = b2;
        for (int h = 0; h < M_HIDDEN; h++) {
            z2 += a[h] * w2[h];
        }

        double y = σ2.f(z2);

        final double eps = 1E-14;
        final double y_clamped = Math.clamp(y, eps, 1-eps);
        double L =  - y0 * log(y_clamped)
                - (1 - y0) * log(1 - y_clamped);

        final double dL_dz2 = y - y0;

        for (int i = 0; i < M_HIDDEN; i++) {
            double dLdz1 = dL_dz2 * w2[i]
                    * σ1.f_deriv(z1[i]);

            for (int x_idx = 0; x_idx < x.length; x_idx++) {
                int xi = x[x_idx];
                double act = x_act[x_idx];

                w1[i][xi] -= lr * dLdz1 * act;
            }

            b1[i] -= lr * dLdz1;
        }

        for (int i = 0; i < M_HIDDEN; i++) {
            double dz2_dw2 = a[i];
            this.w2[i] -= lr * dL_dz2 * dz2_dw2;
        }

        b2 -= lr * dL_dz2;

        return L;
    }

    BinaryClassifierModel(int inputLayerSize,
                          int hiddenLayerSize,
                          InputActivationMode inputActivationMode)
    {
        N_INPUTS = inputLayerSize;
        M_HIDDEN = hiddenLayerSize;

        b1 = new double[hiddenLayerSize];
        w1 = new double[hiddenLayerSize][];
        this.inputActivationMode = inputActivationMode;
        for (int i = 0; i < hiddenLayerSize; i++) {
            w1[i] = new double[inputLayerSize];
        }
        w2 = new double[hiddenLayerSize];
    }

    public static BinaryClassifierModel forTraining(int inputLayerSize,
                                                            int hiddenLayerSize,
                                                            InputActivationMode inputActivationMode
                                                    ) {
        BinaryClassifierModel model = new BinaryClassifierModel(inputLayerSize, hiddenLayerSize, inputActivationMode);

        model.initializeWeights();

        return model;
    }

    public static BinaryClassifierModel fromSerialized(Path serializedModel)
            throws IOException
    {
        int inputLayerSize;
        int hiddenLayerSize;
        InputActivationMode iam;

        try (SlopTable table = new SlopTable(serializedModel)) {
            hiddenLayerSize = BinaryClassifierModelSerialization.modelHiddenCount.open(table).get();
            inputLayerSize = BinaryClassifierModelSerialization.modelInputCount.open(table).get();

            iam = InputActivationMode.valueOf(
                    BinaryClassifierModelSerialization.modelInputActivationMode.open(table).get()
            );
        }

        BinaryClassifierModel model = new BinaryClassifierModel(inputLayerSize, hiddenLayerSize, iam);

        model.load(serializedModel);

        return model;
    }

    private void load(Path serializedModel) throws IOException {
        try (SlopTable table = new SlopTable(serializedModel)) {
            var biasHiddenCol = BinaryClassifierModelSerialization.biasHiddenColumn.open(table);
            var weightsInputHiddenCol = BinaryClassifierModelSerialization.weightsInputHiddenColumn.open(table);
            var weightsHiddenOutputCol = BinaryClassifierModelSerialization.weightsHiddenOutputColumn.open(table);

            for (int i = 0; i < M_HIDDEN; i++) {
                b1[i] = biasHiddenCol.get();
                w2[i] = weightsHiddenOutputCol.get();
                w1[i] = weightsInputHiddenCol.get();
            }
        }

        try (SlopTable table = new SlopTable(serializedModel)) {
            var biasOutputCol = BinaryClassifierModelSerialization.biasOutputColumn.open(table);
            b2 = biasOutputCol.get();
        }
    }

    public void save(Path output) throws IOException {
        try (SlopTable table = new SlopTable(output)) {
            var biasHiddenCol = BinaryClassifierModelSerialization.biasHiddenColumn.create(table);
            var weightsInputHiddenCol = BinaryClassifierModelSerialization.weightsInputHiddenColumn.create(table);
            var weightsHiddenOutputCol = BinaryClassifierModelSerialization.weightsHiddenOutputColumn.create(table);

            for (int i = 0; i < M_HIDDEN; i++) {
                biasHiddenCol.put(b1[i]);
                weightsHiddenOutputCol.put(w2[i]);
                weightsInputHiddenCol.put(w1[i]);
            }
        }
        try (SlopTable table = new SlopTable(output)) {
            var biasOutputCol = BinaryClassifierModelSerialization.biasOutputColumn.create(table);
            var inputCntCol = BinaryClassifierModelSerialization.modelInputCount.create(table);
            var hiddenCntCol = BinaryClassifierModelSerialization.modelHiddenCount.create(table);
            var activationMode = BinaryClassifierModelSerialization.modelInputActivationMode.create(table);

            hiddenCntCol.put(M_HIDDEN);
            inputCntCol.put(N_INPUTS);
            activationMode.put(inputActivationMode.name());

            biasOutputCol.put(b2);
        }
    }

    private void initializeWeights() {

        // https://en.wikipedia.org/wiki/Weight_initialization

        final Random random = new Random(451);

        // Initialize the hidden layer
        double hiddenScale = Math.sqrt(σ1.initVariance(N_INPUTS, M_HIDDEN));
        for (int i = 0; i < M_HIDDEN; i++) {
            Arrays.setAll(w1[i], j -> random.nextGaussian() * hiddenScale);
        }


        // Initialize the output layer
        double outputScale = Math.sqrt(σ2.initVariance(M_HIDDEN, 1));
        Arrays.setAll(w2, i -> random.nextGaussian() * outputScale);
    }

    public enum InputActivationMode {
        BINARY,
        COUNTED
    }
}
