package nu.marginalia.wmsa.edge.converting.interpreter;

public interface Instruction {
    void apply(Interpreter interpreter);
    boolean isNoOp();

    InstructionTag tag();
}
