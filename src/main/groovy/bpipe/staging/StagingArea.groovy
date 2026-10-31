package bpipe.staging

import java.nio.file.Path

import groovy.transform.CompileStatic

/**
 * An alternative location that a command's files are moved to for the duration of
 * the command, configured in the <code>stagingAreas</code> block of <code>bpipe.config</code>
 * and selected per command with the <code>staging</code> attribute.
 * <p>
 * Implementations define how canonical paths map into the area and the shell that moves
 * files in and out around the command. Host-side hooks run in the Bpipe process and are
 * for areas where some of the transfer must happen outside the job (eg: object stores).
 *
 * @see StagingAreas
 * @see bpipe.processors.StagingProcessor
 */
@CompileStatic
abstract class StagingArea implements Serializable {

    public static final long serialVersionUID = 0L

    /**
     * File extensions staged alongside a referenced input when a file with that
     * extension exists next to it (see {@link Companions})
     */
    static final List<String> DEFAULT_COMPANIONS = ['.bai', '.crai', '.csi', '.tbi', '.fai', '.dict', '.gzi', '.idx']

    /**
     * Name of the area in the configuration
     */
    String name

    List<String> companions = DEFAULT_COMPANIONS

    /**
     * @return  the directory holding everything staged for the given command
     */
    abstract String stageDir(String pipelineId, String commandId)

    /**
     * @return  the path the command should use for the given canonical input
     */
    abstract String stagedInputPath(String stageDir, Path canonical)

    /**
     * @return  the path the command should write the given canonical output to
     */
    abstract String stagedOutputPath(String stageDir, Path canonical)

    /**
     * @return  the command text wrapped with the shell that stages files in before
     *          and out after it runs
     */
    abstract String wrap(StagingPlan plan, String commandText)

    /**
     * Called in the Bpipe process before the command is started
     */
    void beforeStart(StagingPlan plan) {
    }

    /**
     * Called in the Bpipe process after the command has finished
     */
    void afterComplete(StagingPlan plan, int exitCode) {
    }

    /**
     * Quote a value for safe use as a single word in a bash command
     */
    static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }
}
