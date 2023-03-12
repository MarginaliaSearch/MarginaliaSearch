package nu.marginalia.converting.compiler;

import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.instructions.DomainLink;
import nu.marginalia.converting.instruction.instructions.LoadDomain;
import nu.marginalia.converting.instruction.instructions.LoadDomainLink;
import nu.marginalia.converting.instruction.instructions.LoadDomainRedirect;
import nu.marginalia.model.EdgeDomain;

import java.util.List;

public class RedirectCompiler {

    public void compile(List<Instruction> ret, EdgeDomain from, EdgeDomain to) {
        ret.add(new LoadDomain(to));
        ret.add(new LoadDomainLink(new DomainLink(from, to)));
        ret.add(new LoadDomainRedirect(new DomainLink(from, to)));
    }
}
