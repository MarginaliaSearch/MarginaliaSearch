package nu.marginalia.converting.instruction;

import java.io.Serializable;

public interface Instruction extends Serializable {
    void apply(Interpreter interpreter);
    boolean isNoOp();

    InstructionTag tag();
}
