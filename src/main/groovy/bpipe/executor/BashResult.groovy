package bpipe.executor;


/**
 *  A simple transfer object to handle the result of bash command executed on a remote host
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BashResult implements Serializable {

    /** The standard output as returned by the executed process */
    String stdOutput

    /** The standard error as returned by the executed process */
    String stdError

    /** The process exit code */
    int exitCode

    /** Exception raised by the execution framework */
    Exception exception


}
