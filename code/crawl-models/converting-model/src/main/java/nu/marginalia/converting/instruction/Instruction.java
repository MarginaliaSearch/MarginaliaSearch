package nu.marginalia.converting.instruction;

public interface Instruction {
    void apply(Interpreter interpreter);
    boolean isNoOp();

    InstructionTag tag();
}
