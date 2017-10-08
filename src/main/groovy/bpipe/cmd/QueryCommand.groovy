/*
 * Copyright (c) 2016 MCRI, authors
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package bpipe.cmd

import java.io.Writer;
import java.util.List

import bpipe.Dependencies
import bpipe.GraphEntry
import bpipe.Command;
import bpipe.CommandManager;
import bpipe.Config
import bpipe.OutputMetaData
import groovy.json.JsonOutput;
import groovy.time.TimeCategory;
import groovy.util.logging.Log;

@Log
class QueryCommand extends BpipeCommand {
    
    /**
     * Format of output
     */
    String format = "txt"
    
    public QueryCommand(List<String> args) {
        super("query", args);
    }

    @Override
    public void run(Writer out) {
        
        CliBuilder cli = new CliBuilder()
        cli.with {
            s 'Show information for stage id <arg>', args:1
            f 'Output format [json|txt]', args:1
        }
        
        def opts = cli.parse(this.args)
        if(opts.f) {
            if(opts.f in ["json","txt"]) {
                this.format = opts.f
            }
            else {
                throw new IllegalArgumentException("Format type $opts.f is not recognised.")
            }
        }
        
        if(opts.s) {
            queryOutputsByStageId(opts.s, out)
        }
        else {
            if(opts.arguments()) {
                queryOutputs(opts.arguments(), out)
            }
            else {
                showWholeDependencyGraph()
            }
        }
    }
    
    void queryOutputsByStageId(String stageId, PrintWriter out) {
        
        // Find the commands executed for the given stage
        // Read all the commands
        List<Command> commands = new CommandManager().getCommandsByStatus()
        
        // Find the ones that satisfy the stage spec
        List<Command> stageCommands = commands.grep { it.stageId == stageId }
        
        out.println "The commands for stage $stageId are ${stageCommands*.command}"
 
        out.println "Unsupported option TODO" 
    }
    
    /**
     * Display a dump of the dependency graph for the given files
     * 
     * @param args  list of file names to display dependencies for
     */
    void queryOutputs(List<String> outputFiles, PrintWriter out) {
        
        Dependencies deps = Dependencies.instance
        
        // Start by scanning the output folder for dependency files
        List<OutputMetaData> outputs = deps.scanOutputFolder()
        GraphEntry graph = deps.computeOutputGraph(outputs)
        List results = []
        for(String arg in outputFiles) {
            GraphEntry filtered = graph.filter(arg)
            if(!filtered) {
                System.err.println "\nError: cannot locate output $arg in output dependency graph"
                continue
            }
            
            if(format == "txt") {
                out.println "\n" + " $arg ".center(Config.config.columns, "=")  + "\n"
                out.println "\n" + filtered.dump()
            }
               
           OutputMetaData p = graph.propertiesFor(arg)
           String duration = "Unknown"
           String pendingDuration = "Unknown"
               
           Date stopTime 
           Date startTime 
           if(p.stopTimeMs > 0) {
               stopTime = new Date(p.stopTimeMs)
               startTime = new Date(p.startTimeMs)
               duration = TimeCategory.minus(stopTime,startTime).toString()
               pendingDuration = TimeCategory.minus(new Date(p.startTimeMs),new Date(p.createTimeMs)).toString()
           }
               
           if(format == "txt") {
               out.println """
                   Created:             ${new Date(p.timestamp)}
                   Pipeline Stage:      ${p.stageName?:'Unknown'}
                   Started:             ${startTime?:'Unknown'}
                   Stopped:             ${stopTime?:'Unknown'}
                   Pending Time:        ${pendingDuration}
                   Running Time:        ${duration}
                   Inputs used:         ${p.inputs.join(',')}
                   Command Id:          ${p.commandId}
                   Command:             ${p.command}
                   Preserved:           ${p.preserve?'yes':'no'}
                   Intermediate output: ${p.intermediate?'yes':'no'}
               """.stripIndent()
               out.println("\n" + ("=" * Config.config.columns))
           }
           
           results << [ 
               
                   created:             new Date(p.timestamp),
                   stageName:           p.stageName?:'Unknown',
                   pendingTime:         pendingDuration,
                   runningTime:         duration,
                   inputs:              p.inputs,
                   commandId:           p.commandId,
                   command:             p.command,
                   preserved:           p.preserve,
                   intermediate:        p.intermediate
           ]
        }
        
        if(format == "json") {
           out.println(new JsonOutput().toJson(results))
        }
    }
    
    void showWholeDependencyGraph(PrintWriter out) {
        Dependencies deps = Dependencies.instance
        
        // Start by scanning the output folder for dependency files
        List<OutputMetaData> outputs = deps.scanOutputFolder()
        GraphEntry graph = deps.computeOutputGraph(outputs)
        out.println "\nDependency graph is: \n\n" + graph.dump()
    }
}
