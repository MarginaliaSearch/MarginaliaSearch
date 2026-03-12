package nu.marginalia.classifier.activation;

public class SigmoidActivationFunction implements ActivationFunction {

    @Override
    public double f(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    @Override
    public double f_deriv(double x) {
        double s = f(x);
        return s * (1.0 - s);
    }

    @Override
    public double initVariance(int inputs, int outputs) {
        // https://en.wikipedia.org/wiki/Weight_initialization#Glorot_initialization
        return 2. / (inputs + outputs);
    }

}
