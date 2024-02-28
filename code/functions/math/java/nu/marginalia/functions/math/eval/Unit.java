package nu.marginalia.functions.math.eval;

public class Unit {

    public final String name;
    public final String type;
    public final double baseValue;

    public Unit(String type, double value, String name) {
        this.type = type;
        this.name = name;
        this.baseValue = value;
    }

}
