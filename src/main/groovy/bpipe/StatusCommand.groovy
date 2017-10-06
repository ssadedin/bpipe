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

import groovy.time.TimeCategory
import groovy.util.slurpersupport.GPathResult;
import bpipe.cmd.BpipeCommand;
import bpipe.executor.CommandExecutor;

/**
 * Print out information about currently running pipeline in the local, or if no 
 * running pipeline, the outcome of the previous run.
 * 
 * @author Simon Sadedin
 */
class StatusCommand extends BpipeCommand {

    public StatusCommand() {
        // hack
        super("",[])
    }
    
    void execute(def args) {
        this.args = args
        run(System.out)
    }
    
    void run(Writer out) {
        
        CliBuilder cli = new CliBuilder()
        cli.with {
            's' 'Show summary of commands', longOpt:'summary', args: 0
        }
        
        def opts= cli.parse(args)
        if(opts.s) {
            
            CommandManager mgr = new CommandManager()
            
            out.println ""
            out.println "Analyzing executed commands ...."
            out.println ""
            List<Command> commands = mgr.getCurrentCommands()
            
            commands += mgr.getCommandsByStatus(null)
            
            Map<CommandStatus,Command> grouped = commands.groupBy {
                it.status
            }
            
            out.println "-" * Config.config.columns
            
            out.println "Waiting: ".padRight(12) + String.valueOf(grouped[CommandStatus.WAITING]?.size()?:0).padLeft(8)
            out.println "Running: ".padRight(12) + String.valueOf(grouped[CommandStatus.RUNNING]?.size()?:0).padLeft(8)
            out.println "Completed: ".padRight(12) + String.valueOf(grouped[CommandStatus.COMPLETE]?.size()?:0).padLeft(8)
            out.println "Failed: ".padRight(12) + String.valueOf(commands.count { it.exitCode != 0 }).padLeft(8)
            
            out.println "-" * Config.config.columns
        }
        else
        if(isRunning()) {
            showCurrent(out)
        }
        else {
            String pid = getLastLocalPID()
            File resultFile = new File(".bpipe/results/${pid}.xml")
            
            if(!resultFile.exists()) {
                out.println """
                    Error: no result file exists for the most recent Bpipe run.
                
                    This may indicate that the Bpipe process was terminated in an unexpected manner.
                """
                System.exit(1)
            }
            
            GPathResult dom = new XmlSlurper().parse(resultFile)
            out.println(" " + (" Pipeline " + (dom.succeeded.text() == "true" ? "Succeeded" : "Failed")).center(Config.config.columns-2,"="))
            out.println(("| Started: " + dom.startDateTime.text()).padRight(Config.config.columns-1) + "|")
            out.println(("| Ended: " + dom.endDateTime.text()).padRight(Config.config.columns-1) + "|")
            out.println (" " + "="*(Config.config.columns-2))
            out.println ""

            dom.commands.command.each { cmdNode ->
                out.println (" Stage ${cmdNode.stage.text()} Command ${cmdNode.id.text()} ".center(Config.config.columns,"="))
                out.println "Create: \t${cmdNode.start.text()}"
                out.println "Start: \t${cmdNode.start.text()}"
                out.println "End: \t${cmdNode.end.text()}"
                out.println "Exit Code: ${cmdNode.exitCode.text()}"
            }
        }
    }
    
    void showCurrent(PrintWriter out) {
        
        CommandManager mgr = new CommandManager()
        
        // List all the commands in the commands folder
        List<Command> commands = mgr.getCurrentCommands()
        
        out.println "\nFound ${commands.size()} currently executing commands:\n"
        
        Date now = new Date()
        
        commands.eachWithIndex { cmd, i -> 
            out.println Utils.indent("${i+1}.".padRight(6) + cmd.executor.statusMessage())
            out.println Utils.indent("      " + cmd.status.name() + " for " + TimeCategory.minus(now,new Date(cmd.createTimeMs)))
            out.println ""
        }
    }
}
