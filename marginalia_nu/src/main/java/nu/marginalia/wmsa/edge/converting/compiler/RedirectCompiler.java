package nu.marginalia.wmsa.edge.converting.compiler;

import nu.marginalia.wmsa.edge.converting.interpreter.Instruction;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.DomainLink;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadDomain;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadDomainLink;
import nu.marginalia.wmsa.edge.converting.interpreter.instruction.LoadDomainRedirect;
import nu.marginalia.wmsa.edge.model.EdgeDomain;

import java.util.List;

public class RedirectCompiler {

    public void compile(List<Instruction> ret, EdgeDomain from, EdgeDomain to) {
        ret.add(new LoadDomain(to));
        ret.add(new LoadDomainLink(new DomainLink(from, to)));
        ret.add(new LoadDomainRedirect(new DomainLink(from, to)));
    }
}
