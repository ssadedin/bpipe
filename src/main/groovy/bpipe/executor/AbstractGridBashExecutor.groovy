package bpipe.executor

import bpipe.CommandStatus

import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.logging.Logger;

/**
 *  An abstract executor for distributed data-grid based
 *  on the {@link java.util.concurrent.ExecutorService} model
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
abstract class AbstractGridBashExecutor implements CommandExecutor {

    /**
     * Logger to use with this class
     */
    private static Logger log = Logger.getLogger("bpipe.executor.AbstractGridBashExecutor");
    
    def Map cfg;

    def String id;

    def String name;

    def String cmd;

    transient protected ExecutorService executor

    transient protected Future<BashResult> task

    transient protected ExecutorServiceProvider provider

    protected BashResult result

    def getExecutor() { executor }

    def getTask() { task }

    def getResult() { result }

    AbstractGridBashExecutor( ExecutorServiceProvider provider ) {
        this.provider = provider
    }


    @Override
    void start(Map cfg, String id, String name, String cmd) {

        this.cfg = cfg
        this.id = id
        this.name = name
        this.cmd = cmd ?. trim()

        /*
         * submit to the Hazelcast grid for command execution
         */
        log.info "Executing command '${cmd}' with ${provider.getName()}"
        executor = provider.getExecutor()
        task = executor.submit(new BashCallableCommand(cmd) );
    }

    @Override
    void stop() {
        log.warning("Stop not supported for ${provider.name} executor")
    }

    @Override
    void cleanup() { }

    @Override
    List<String> getIgnorableOutputs() { return null }


    @Override
    String status() {

        if( task && result ) {
            return CommandStatus.COMPLETE
        }

        if( !task?.isDone() && !task?.isCancelled() ) {
            return CommandStatus.RUNNING
        }

        return CommandStatus.UNKNOWN

    }

    @Override
    int waitFor() {

        if( !task ) {
            return -1
        }

        /*
        * display the produced output
        */
        result = task.get()

        if( result?.stdOutput ) { System.out.println(result.stdOutput) }
        if( result?.stdError ) { System.out.println(result.stdError) }

        return result?.exitCode
    }



}
