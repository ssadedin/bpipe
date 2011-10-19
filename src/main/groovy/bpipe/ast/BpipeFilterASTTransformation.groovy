package bpipe.ast
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.transform.GroovyASTTransformation;

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)    
class BpipeFilterASTTransformation  extends BpipeASTTransformation {

    public BpipeFilterASTTransformation() {
        super("filter");
    }
}
