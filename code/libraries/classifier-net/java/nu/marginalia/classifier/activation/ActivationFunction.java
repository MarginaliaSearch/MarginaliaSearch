package nu.marginalia.classifier.activation;

public interface ActivationFunction {

    /** Activation function */
    public double f(double x);

    /** Derivative of activation function */
    public double f_deriv(double x);

    /** Variance of hidden value initialization */
    public double initVariance(int inputs, int outputs);
}
