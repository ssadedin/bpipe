/*
 * Copyright (c) Murdoch Childrens Research Institute and Contributers
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package bpipe.executor

import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import groovy.util.logging.Log;

/**
 *  An abstract executor for distributed data-grid based
 *  on the {@link java.util.concurrent.ExecutorService} model
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
abstract class AbstractGridBashExecutor implements CommandExecutor {

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


    @Override
    void start(Map cfg, String id, String name, String cmd, File outputDirectory) {

        this.cfg = cfg
        this.id = id
        this.name = name
        this.cmd = cmd ?. trim()

        /*
         * submit to the grid for command execution
         */
        log.info "Executing command '${cmd}' with ${provider.getName()}"
        executor = provider.getExecutor()
        def bashCmd = new BashCallableCommand(cmd)
        bashCmd.outputDirectory = outputDirectory
        task = executor.submit( bashCmd );
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
