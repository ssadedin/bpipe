package bpipe.layers

/**
 * The shell contexts a command passes through on the compute resource, from outermost
 * to innermost.
 * <p>
 * Everything in these layers runs where the job runs (eg: the HPC compute node or the
 * cloud instance), not where Bpipe runs.
 */
enum CommandLayer {

    /**
     * The job shell started by the executor, outside any context boundary such as a
     * container. Hooks here can use the tools and credentials of the compute host.
     */
    JOB,

    /**
     * The context the command itself runs in: inside the container, if one is configured.
     */
    INNER
}
