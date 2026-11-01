package bpipe.staging

import java.nio.file.Path
import java.nio.file.Paths

import bpipe.layers.CommandLayer
import bpipe.layers.CommandLayers
import bpipe.layers.ShellHook

import groovy.transform.CompileStatic

/**
 * A staging area that is a directory visible to the job, typically fast node-local
 * scratch storage on an HPC compute node.
 * <p>
 * All staging happens inside the job: inputs are copied to the area before the command,
 * and declared outputs are moved back after it succeeds. Staged files are always removed.
 * <p>
 * Files are staged under a per-command directory, mirroring their canonical absolute path:
 * <pre>
 * &lt;path&gt;/bpipe-&lt;user&gt;-&lt;pid&gt;-&lt;command id&gt;/&lt;canonical absolute path&gt;
 * </pre>
 */
@CompileStatic
class DirectoryStagingArea extends StagingArea {

    public static final long serialVersionUID = 0L

    /**
     * Absolute path of the directory files are staged into
     */
    String path

    void setPath(String path) {
        this.@path = Paths.get(path).toAbsolutePath().normalize().toString()
    }

    @Override
    String stageDir(String pipelineId, String commandId) {
        String user = System.getProperty('user.name')
        return Paths.get(path, "bpipe-$user-$pipelineId-$commandId".toString()).toString()
    }

    @Override
    String stagedInputPath(String stageDir, Path canonical) {
        return mirror(stageDir, canonical)
    }

    @Override
    String stagedOutputPath(String stageDir, Path canonical) {
        return mirror(stageDir, canonical)
    }

    private static String mirror(String stageDir, Path canonical) {
        assert canonical.isAbsolute()
        return stageDir + canonical.normalize().toString()
    }

    /**
     * Stages files in the {@link CommandLayer#JOB} layer: the area is on the compute host,
     * so copying must happen there, outside any container the command runs in.
     */
    @Override
    void addHooks(StagingPlan plan, CommandLayers layers) {
        if(plan.isEmpty())
            return
        layers.add(CommandLayer.JOB, new ShellHook(name: "staging:$name", pre: stageIn(plan), post: stageOut(plan)))
    }
    
    private String stageIn(StagingPlan plan) {
        StringBuilder b = new StringBuilder()
        
        List<String> dirs = (plan.inputs.values() + plan.outputs.values()).collect { 
            Paths.get(it).parent.toString() 
        }.unique()
        
        b.append("mkdir -p ${dirs.collect { shellQuote(it) }.join(' ')} || ")
         .append("{ echo ${shellQuote('Bpipe staging: failed to create staging directory ' + plan.stageDir)} >&2; exit 1; }\n")
        
        plan.inputs.each { String canonical, String staged ->
            b.append("cp -p ${shellQuote(canonical)} ${shellQuote(staged)} || ")
             .append("{ echo ${shellQuote('Bpipe staging: failed to stage input ' + canonical + ' to ' + staged)} >&2; exit 1; }\n")
        }
        return b.toString()
    }
    
    /**
     * Moves outputs back if the command succeeded, and always removes the stage directory
     */
    private String stageOut(StagingPlan plan) {
        StringBuilder b = new StringBuilder()
        if(plan.outputs) {
            b.append('if [ "$__bpipe_rc" -eq 0 ]; then\n')
            plan.outputs.each { String canonical, String staged ->
                String dir = Paths.get(canonical).parent.toString()
                b.append("    if [ -e ${shellQuote(staged)} ]; then ")
                 .append("mkdir -p ${shellQuote(dir)} && mv -f ${shellQuote(staged)} ${shellQuote(canonical)} || ")
                 .append("{ echo ${shellQuote('Bpipe staging: failed to return output ' + canonical)} >&2; __bpipe_rc=1; }; fi\n")
            }
            b.append('fi\n')
        }
        b.append("rm -rf ${shellQuote(plan.stageDir)}\n")
        return b.toString()
    }
}
