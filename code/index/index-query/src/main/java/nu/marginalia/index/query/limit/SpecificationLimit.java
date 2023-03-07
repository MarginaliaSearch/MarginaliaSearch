package nu.marginalia.index.query.limit;

import lombok.ToString;

public record SpecificationLimit(SpecificationLimitType type, int value) {
    public static SpecificationLimit none() {
        return new SpecificationLimit(SpecificationLimitType.NONE, 0);
    }

    public static SpecificationLimit equals(int value) {
        return new SpecificationLimit(SpecificationLimitType.EQUALS, value);
    }

    public static SpecificationLimit lessThan(int value) {
        return new SpecificationLimit(SpecificationLimitType.LESS_THAN, value);
    }

    public static SpecificationLimit greaterThan(int value) {
        return new SpecificationLimit(SpecificationLimitType.GREATER_THAN, value);
    }

    public boolean test(int parameter) {
        if (type == SpecificationLimitType.NONE)
            return true;
        if (type == SpecificationLimitType.EQUALS)
            return parameter == value;
        if (type == SpecificationLimitType.GREATER_THAN)
            return parameter >= value;
        if (type == SpecificationLimitType.LESS_THAN)
            return parameter <= value;
        throw new AssertionError("Unknown type " + type);
    }

    @Override
    public String toString() {
        if (type == SpecificationLimitType.NONE)
            return type.toString();

        else return "%s:%d".formatted(type, value);
    }
}
