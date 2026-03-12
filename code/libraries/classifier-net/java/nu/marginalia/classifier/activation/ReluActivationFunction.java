package nu.marginalia.classifier.activation;

public class ReluActivationFunction implements ActivationFunction {
    
    @Override
    public double f(double x) {
        if (x > 0)
            return x;
        return 0;
    }

    @Override
    public double f_deriv(double x) {
        if (x > 0)
            return 1;
        return 0;
    }

    @Override
    public double initVariance(int inputs, int outputs) {
        // https://en.wikipedia.org/wiki/Weight_initialization#He_initialization
        return 2. / inputs;
    }

}
