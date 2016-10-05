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
package bpipe

import groovy.time.TimeCategory;
import bpipe.executor.CommandExecutor;

class StatusCommand {

    public StatusCommand() {
    }
    
    void execute(def args) {
        
        CliBuilder cli = new CliBuilder()
        cli.with {
            's' 'Show summary of commands', longOpt:'summary', args: 0
        }
        
        def opts= cli.parse(args)
        if(opts.s) {
            
            println ""
            println "Analyzing executed commands ...."
            println ""
            List<Command> commands = CommandManager.getCurrentCommands()
            
            commands += CommandManager.getCommands(new File(CommandManager.DEFAULT_EXECUTED_DIR))
            
            Map<CommandStatus,Command> grouped = commands.groupBy {
                it.status
            }
            
            println "-" * Config.config.columns
            
            println "Waiting: ".padRight(12) + String.valueOf(grouped[CommandStatus.WAITING]?.size()?:0).padLeft(8)
            println "Running: ".padRight(12) + String.valueOf(grouped[CommandStatus.RUNNING]?.size()?:0).padLeft(8)
            println "Completed: ".padRight(12) + String.valueOf(grouped[CommandStatus.COMPLETE]?.size()?:0).padLeft(8)
            println "Failed: ".padRight(12) + String.valueOf(commands.count { it.exitCode != 0 }).padLeft(8)
            
            println "-" * Config.config.columns
        }
        else {
            showCurrent()
        }
    
    }
    
    void showCurrent() {
        
        // List all the commands in the commands folder
        List<Command> commands = CommandManager.getCurrentCommands()
        
        println "\nFound ${commands.size()} currently executing commands:\n"
        
        Date now = new Date()
        
        commands.eachWithIndex { cmd, i -> 
            println Utils.indent("${i+1}.".padRight(6) + cmd.executor.statusMessage())
            println Utils.indent("      " + cmd.status.name() + " for " + TimeCategory.minus(now,new Date(cmd.createTimeMs)))
            println ""
        }
    }
}
