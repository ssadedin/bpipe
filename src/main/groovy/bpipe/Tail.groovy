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

import java.io.BufferedReader;

import bpipe.executor.CommandExecutor
import groovy.util.logging.Log;;

/**
 * Custom support for tailing the log file.
 * <p>
 * This job used to be done by using the 'tail' command directly,
 * but now that the log files contain more meta data that approach 
 * became impractical, especially since problems with buffering 
 * make it extremely complicated to pipe tail output to other commands.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Log
class Tail {
    
    final static int DEFAULT_LINES = 72
    
    static CliBuilder logCli = new CliBuilder(usage: "log <options> [<jobid>]", posix: true)
    
    static void main(String [] args) {
        logCli.with {
            n longOpt: 'lines', 'number of lines to log', args:1
            t longOpt: 'threads', 'thread id to track', args:1
            c longOpt: 'command', 'command id to show output for', args:1
            s longOpt: 'stageid', 'stage id to show output for', args:1
            e longOpt: 'errors', 'show output for commands that failed in the last run'
            f longOpt: 'follow', 'keep following file until user presses Ctrl+C'
            x longOpt: 'completed', 'show in context of completed pipeline with given pid', args:1
            v longOpt: 'verbose', 'enable verbose logging'
            h longOpt: 'help', 'show help'
        }
        
        
        def opts = logCli.parse(args)
        if(!opts) {
            System.exit(1)
        }
        
        if(opts.h) {
            logCli.usage()
            System.exit(0)
        }
        
        Utils.configureSimpleLogging(".bpipe/bpipe.log")
        if(opts.v)
            Utils.configureVerboseLogging()
        
        int lines = opts.n ? opts.n.toInteger() : DEFAULT_LINES
        String threadId = opts.t ?: null
        
        if(opts.arguments().size()==0) {
            logCli.usage()
            System.exit(1)
        }
        
        String jobId = opts.arguments()[0]
        checkValidJobId(jobId)
            
        
        // Open the file
        File logFile = getJobLogFile(jobId)
        if(opts.e) {
            showFailedCommands(logFile)
        }
        else
        if(opts.c) {
            showCommandLog(logFile, opts.c)
        }
        else
        if(opts.s) {
            showStageLog(logFile, opts.s)
        }
        else {
            showTail(logFile, lines, threadId, opts)
        }
    }
    
    static File getJobLogFile(String jobId) {
       new File(".bpipe/logs/${jobId}.log")     
    }
    
    /**
     * Check that the given job id is valid and that log files exist,
     * and exit with appropriate error messages if not. 
     * 
     * @param jobId
     */
    static void checkValidJobId(String jobId) {
        if(jobId == "-1") {
            println ""
            println "ERROR: could not find any Bpipe jobs in this directory!"
            println ""
            println "Has bpipe been run in this directory?"
            println ""
            System.exit(1)
        }
        
        File logFile = getJobLogFile(jobId)
        if(!logFile.exists()) {
            println ""
            println "ERROR: no log file found for run $jobId"
            println ""
            println "Although a Bpipe run occurred, the log file could not be found. This may indicate the .bpipe directory has been corrupted."            
            println ""
            System.exit(1)
        }
    }
    
    static void showFailedCommands(File logFile) {
        
        // Open the most recent results file
        def results = new File(".bpipe/results").listFiles()?.grep {it.name.endsWith('.xml') }?.max { it.lastModified() }
        if(!results) {
            println "\nNo Bpipe pipeline results were found yet in this directory.\n"
            return
        }
        
        // Parse the XML to find the commands that failed
        def failedCommands =  
            new XmlSlurper().parse(results).commands.command.grep { it.exitCode.text() !="0" }.id*.text()
        
        def runId = results.name.replaceAll('[^0-9]','')
        
        println ""
        println " Found ${failedCommands.size()} failed commands from run ${runId} ".center(Config.config.columns,'=')
        println ""
        
        failedCommands.each {
            showCommandLog(logFile, it)
        }
    }
    
    /**
     * Scan the log file for commands executed by the given stage and dump their output.
     * 
     * @param logFile
     * @param stageId
     */
    static void showStageLog(File logFile, String stageId) {
        
        log.info "Searching for commands for stage $stageId in log file " + logFile.absolutePath
        
        // Read all the commands
        List<Command> commands = new CommandManager().getCommandsByStatus()
        
        // Find the ones that satisfy the stage spec
        List<String> ids = commands.grep { it.stageId == stageId }.collect { Command cmd -> cmd.id }
        
        if(ids.empty) {
            println "No commands match specified stage id: $stageId"
        }
        else {
            log.info "Found commands: " + ids.join(',')
        }
        
        logFile.withReader { r ->
            OutputLogIterator i = new OutputLogIterator(r)
            i.each {  OutputLogEntry e ->
                if(e.commandId in ids) {
                    println e.content
                }
            }
        }            
    }
    
    /**
     * Identify the entries for the given command in the given log file and display them,
     * along with other command information.
     * 
     * @param logFile
     * @param commandId
     */
    static void showCommandLog(File logFile, String commandId) {
        OutputLogEntry logEntry = logFile.withReader { r ->
            OutputLogIterator i = new OutputLogIterator(r)
            i.find { it.commandId == commandId }
        }
        
        // Try to also show the command information
        Command cmd = new CommandManager().readSavedCommand(commandId)
        
        println " Command ${cmd?.name?:''} ($commandId) ".center(Config.config.columns, "=")
        
        int leftWidth = 10
        if(cmd) {
            
            
            Date startTime
            Date stopTime
            if(cmd.stopTimeMs > 0) {
                stopTime = new Date(cmd.stopTimeMs)
            }
            if(cmd.startTimeMs > 0) {
                startTime = new Date(cmd.startTimeMs)
            }

            println ""
            println "Command ".padRight(leftWidth) + " : " + cmd.command
            println "Started ".padRight(leftWidth) + " : " + startTime?:'Unknown'
            println "Stopped ".padRight(leftWidth) + " : " + stopTime?:'Unknown'
            println "Exit Code ".padRight(leftWidth) + " : " + cmd.exitCode
            println "Config: " 
            Utils.table(["Name","Value"], cmd.processedConfig.collect { k,v -> [k,v] }, indent:leftWidth)
            
            println "\nOutput ".padRight(leftWidth) + " : "
            println ""
            if(logEntry) {
                println logEntry.content
            }
            else {
                println "No command output found in most recent log file"
            }
        }
        else {
            println ""
            println "No record of command $commandId was saved. This may indicate a severe error occurred."
            println ""
        }
        
        println ""
    }
    
    static void showTail(File logFile, int lines, String threadId, def opts) {
        
        if(opts.x) {
            println ""
            println "MSG: vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv"
            println "MSG:     NOTE: Pipeline completed as process $opts.x.  Trailing lines follow."
            println "MSG:     Use bpipe log -n <lines> to see more lines"
            println "MSG: ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^"
            println ""
        }
        
        int countSkip = 0
        BufferedReader r 
        try {
          int backwardsBytes = lines*250
          List buffer = []
          boolean first = true
          while(buffer.size()<lines) {
              
            if(r != null) {
                r.close()
                backwardsBytes = backwardsBytes * 2
            }
            
            r = new BufferedReader(new FileReader(logFile))
            
            int skipLength = logFile.length() - backwardsBytes
            if(skipLength>0) {
              r.skip(skipLength)
            }
          
            // Attempt to read the requested number of lines
            while(true) {
                
                String line = r.readLine()
                if(line == null) {
                    break
                }
                
                // First line may be "partial" since we just jumped to an arbitrary byte offset
                if(first) {
                    first = false
                    if(skipLength>0) // If we didn't skip any bytes then the first line is 
                                     // the actual first line of the log file; don't omit it.
                        continue
                }
                
                // Extract the thread id
                int tabIndex = line.indexOf('\t')
                String log = line
                if(tabIndex > 0) {
                  String metaData = line.substring(0, tabIndex)
                  log = line.size() > tabIndex+1 ? line.substring(tabIndex+1) : ""
                  if(threadId && metaData.indexOf(threadId)!=1) 
                      continue
                }
                buffer << log
                if(buffer.size() > lines)
                    buffer.remove(0)
            }
            
            // If already read past start of file, no point in looping even if we did not fill enough lines
            if(backwardsBytes > logFile.length())
                    break
          }
          
          buffer.each { println(it) }
          
          if(!opts.f)
              System.exit(0)
          
          while(true) {
              String line = r.readLine()
              if(line == null) {
                  Thread.sleep(1000)
              }
              else {
                int tabIndex = line.indexOf('\t')
                if(tabIndex>=0) {
                  String metaData = line.substring(0, tabIndex)
                  if(threadId && metaData.indexOf(threadId)!=1)
                      continue
                  println line.substring(line.indexOf('\t')+1)
                }
                else
                  println line
              }
          }
        }
        finally {
            if(r)
              r.close()
        } 
    }
}
