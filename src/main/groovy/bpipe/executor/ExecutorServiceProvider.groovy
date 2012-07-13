package bpipe.executor

import java.util.concurrent.ExecutorService

/**
 *  Define the interface for a provider of distributed execution service
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface ExecutorServiceProvider {

    def getName()

    def ExecutorService getExecutor()

}
