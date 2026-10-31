package bpipe.staging

import groovy.transform.CompileStatic
import groovy.transform.ToString

/**
 * The concrete staging actions for one command: which files are moved where.
 * <p>
 * Paths are held as strings: keys are canonical absolute paths (where Bpipe tracks
 * the file), values are the paths (or URLs) the command sees in the staging area.
 */
@CompileStatic
@ToString(includeNames=true)
class StagingPlan implements Serializable {

    public static final long serialVersionUID = 0L

    /**
     * Per-command directory in the staging area. Everything under it is discarded
     * when the command finishes.
     */
    String stageDir

    /**
     * Inputs (including companion files) to stage: canonical path => staged path
     */
    Map<String,String> inputs = new LinkedHashMap<String,String>()

    /**
     * Declared outputs: canonical path => staged path. Each one that exists in the
     * staging area after a successful command is moved back.
     */
    Map<String,String> outputs = new LinkedHashMap<String,String>()

    boolean isEmpty() {
        return inputs.isEmpty() && outputs.isEmpty()
    }
}
