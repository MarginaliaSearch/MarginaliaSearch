package nu.marginalia.converting.compiler;

import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.instructions.DomainLink;
import nu.marginalia.converting.instruction.instructions.LoadDomain;
import nu.marginalia.converting.instruction.instructions.LoadDomainLink;
import nu.marginalia.converting.instruction.instructions.LoadDomainRedirect;
import nu.marginalia.model.EdgeDomain;

import java.util.List;
import java.util.function.Consumer;

public class RedirectCompiler {

    public void compile(Consumer<Instruction> instructionConsumer, EdgeDomain from, EdgeDomain to) {
        instructionConsumer.accept(new LoadDomain(to));
        instructionConsumer.accept(new LoadDomainLink(new DomainLink(from, to)));
        instructionConsumer.accept(new LoadDomainRedirect(new DomainLink(from, to)));
    }
}
