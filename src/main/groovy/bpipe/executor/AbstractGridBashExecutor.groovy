
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

import bpipe.Command;
import bpipe.CommandStatus;
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
    
    transient Command command

    transient protected ExecutorService executor

    transient protected Future<BashResult> task

    transient protected ExecutorServiceProvider provider
    
    transient protected Appendable outputLog
    
    transient protected Appendable errorLog

    protected BashResult result

    def getExecutor() { executor }

    def getTask() { task }

    def getResult() { result }


    @Override
    void start(Map cfg, Command command, Appendable outputLog, Appendable errorLog) {

        this.cfg = cfg
        this.id = command.id
        this.name = command.name
        this.cmd = command.command ?. trim()
        
        this.outputLog = outputLog
        this.errorLog = errorLog

        /*
         * submit to the grid for command execution
         */
        log.info "Executing command '${cmd}' with ${provider.getName()}"
        executor = provider.getExecutor()
        def bashCmd = new BashCallableCommand(cmd)
        bashCmd.outputDirectory = new File(".")
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
        String result = statusImpl()
        return this.command.status = result
    }
    
    String statusImpl() {

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
        
        if(command) {
            command.status = CommandStatus.COMPLETE.name()
        }

        if( result?.stdOutput ) { outputLog.println(result.stdOutput) }
        if( result?.stdError ) { errorLog.println(result.stdError) }

        return result?.exitCode
    }

    @Override
    public String statusMessage() {
        return "Grid job command: $cmd"
    }
}
