package nu.marginalia.classifier;

import nu.marginalia.slop.column.array.DoubleArrayColumn;
import nu.marginalia.slop.column.primitive.DoubleColumn;
import nu.marginalia.slop.column.primitive.IntColumn;
import nu.marginalia.slop.column.string.StringColumn;

public class BinaryClassifierModelSerialization {
    public static final DoubleArrayColumn weightsInputHiddenColumn =
            new DoubleArrayColumn("weights-input-hidden");
    public static final DoubleColumn biasHiddenColumn =
            new DoubleColumn("bias-hidden");
    public static final DoubleColumn weightsHiddenOutputColumn =
            new DoubleColumn("weights-hidden-output");
    public static final DoubleColumn biasOutputColumn =
            new DoubleColumn("bias-output");

    public static final IntColumn modelInputCount = new IntColumn("model-input-count");
    public static final IntColumn modelHiddenCount = new IntColumn("model-hidden-count");
    public static final StringColumn modelInputActivationMode = new StringColumn("model-hidden-count");


}
