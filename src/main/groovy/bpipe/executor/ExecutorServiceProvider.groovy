package bpipe.executor

import java.util.concurrent.ExecutorService

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface ExecutorServiceProvider {

    def getName()

    def ExecutorService getExecutor()

}
