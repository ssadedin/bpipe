package bpipe.executor;


/**
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
