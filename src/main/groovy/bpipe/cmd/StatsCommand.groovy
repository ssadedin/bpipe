/*
 * Copyright (c) 2017 MCRI, authors
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

import java.io.PrintStream

import groovy.time.TimeCategory
import groovy.util.slurpersupport.GPathResult

import static org.fusesource.jansi.Ansi.*
import static org.fusesource.jansi.Ansi.Color.*
import org.fusesource.jansi.AnsiConsole

import bpipe.Config
import bpipe.Utils

class StatsCommand extends BpipeCommand {
    public StatsCommand(List<String> args) {
        super("stats", args);
    }

    @Override
    public void run(Writer out) {
        


        cli.with {
            all 'Show aggregate results for all runs of this pipeline', required: false
            stage 'Show one row per instance of the named stage', args:1, argName:'name'
        }
        def opts = this.parse()
        
        // hack: need to refactor out to be set in constructor
        this.out = out;
        
        List<File> resultFiles
        if(opts.all) {
            resultFiles = new File(".bpipe/results").listFiles().grep { it.name.endsWith('.xml') }
        }
        else {
            List<String> positional = (opts.arguments() ?: []) as List<String>
            String pid = positional ? positional[0] : this.getLastLocalPID()

            if(pid == null) {
                out.println "\nNo Bpipe run could be found in this directory\n"
                System.exit(1)
            }

            resultFiles = [getResultFile(pid)]
        }
        
        Closure<Date> toDate = { value ->
            Date.parse('yyyy-MM-dd HH:mm:ss', value.text())
        }
        
        
        List<GPathResult> doms = resultFiles.collect { 
            try {
                new XmlSlurper().parse(it) 
            }
            catch(Exception e) {
                out.println "WARNING: Could not read result file: " + it
                return null
            }
        }.grep { it != null }
        
        if(doms.isEmpty()) {
            out.println "\nNo result files could be read.\n"
            System.exit(1)
        }
        
        long nowMs = System.currentTimeMillis()
        
        long totalTimeMs = doms.collect { dom -> 
            (dom.endDateTime.text()?toDate(dom.endDateTime).time:nowMs) - toDate(dom.startDateTime).time 
        }.sum()
        
        String successMessage = "Succeeded"
        if(doms.any { it.endDateTime.size() == 0 }) {
            successMessage = "Running"
        }

        String overallStatus = (doms.every { it.succeeded.text() == "true"} ? successMessage : "Failed")
        
        String runTime = formatTimeSpan(totalTimeMs)
        out.println ""
        out.println(" " + " Pipeline $overallStatus ".center(Config.config.columns-2,"="))
        out.println(("| Started: " + doms[0].startDateTime.text()).padRight(Config.config.columns-1) + "|")
        out.println(("| Ended: " + doms[-1].endDateTime.text()).padRight(Config.config.columns-1) + "|")
        out.println(("| Run Time: " + runTime).padRight(Config.config.columns-1) + "|")
        out.println (" " + "="*(Config.config.columns-2))
        out.println ""
        
        long pipelineStartTimeMs =  toDate(doms[0].startDateTime).time
        long pipelineEndTimeMs =  doms[-1].endDateTime.text() ? toDate(doms[-1].endDateTime).time : System.currentTimeMillis()
        long pipelineTotalMs = pipelineEndTimeMs - pipelineStartTimeMs

        if(opts.stage) {
            renderStageInstances((String)opts.stage, doms, toDate, pipelineStartTimeMs, pipelineTotalMs)
            out.println ""
            return
        }

        List<List> stats = doms.collect { dom ->dom.commands.command }.sum().groupBy {  cmdNode ->
            cmdNode.stage.text()
        }.collect { stage, cmds ->
            
            // Failed commands do not accurately reflect the time taken, so
            // only count commands that succeeded
            List succeeded = cmds.grep { cmd ->
                cmd.exitCode.text() == "0"
            }
            
            List valid = succeeded.grep { cmd ->
                // Bug where start times not initialised can have caused historical entries to have this
                !cmd.start.text().startsWith('1970')
            }
            
            if(valid.isEmpty()) {
                return null
            }

            List<Long> times = valid.collect { cmd ->  
                long startTimeMs = toDate(cmd.start).time 
                startTimeMs == 0 ? 0 : toDate(cmd.end).time - startTimeMs
            }
            
            List<Long> startTimes = valid.collect { cmd -> def t = toDate(cmd.start).time; return t;  }
            long minStart = startTimes.min()
            long minStartRel = minStart != null ?  minStart - pipelineStartTimeMs : -1
            long maxEnd = valid.collect { cmd ->  
                long startTimeMs = toDate(cmd.start).time;
                return  startTimeMs == 0 ? 0 : toDate(cmd.end).time
            }.max() - pipelineStartTimeMs 
            
            double mean = times.isEmpty() ? 0 : times.sum() / times.size()

            String timing = (minStartRel < 0) ? '' : formatTimingBar(minStartRel, maxEnd, pipelineTotalMs, 60)

            def cores = valid.collect { cmd -> cmd.resources?.procs }.find { it }?: "-"

            return [
             stage,
             cmds.size(),
             formatTimeSpan(times.min()?.toLong()),
             formatTimeSpan(mean), formatTimeSpan(times.max()),
             cores,
             ((times.sum()?:0) / 1000.0).toLong(),
             timing
            ]
        }
        .grep { it != null }
        
        Utils.table(["Stage","Count","Min","Mean","Max", "Cores","Weight","Timing"], stats, indent:1)
        
        out.println ""
    }
    
    String formatTimeSpan(t) {
        if(t==null)
            return "-"

        long longTime = t.toLong()
        if(longTime == 0) {
            return "-"
        }

        TimeCategory.minus(new Date(longTime), new Date(0)).toString()
                .replaceAll('\\.[0-9]{1,} seconds$',' seconds')
                .replaceAll('minutes,.* seconds$',' minutes')
    }

    String formatTimingBar(long startRel, long endRel, long pipelineTotalMs, int width) {
        if(pipelineTotalMs <= 0)
            return ''

        int barWidth = (int)(width * ((endRel - startRel) / (double)pipelineTotalMs))
        if(barWidth <= 0)
            barWidth = 1

        String bar
        if(barWidth == 1)
            bar = "H"
        else if(barWidth == 2)
            bar = "├┤"
        else
            bar = '├' + '─'*(barWidth-2) + '┤'

        (" ") * (int)(width * (startRel / (double)pipelineTotalMs)) + bar
    }

    void renderStageInstances(String stageName, List<GPathResult> doms, Closure<Date> toDate,
                              long pipelineStartTimeMs, long pipelineTotalMs) {

        def allCmdsRaw = doms.collect { dom -> dom.commands.command }.sum()
        List allCmds = allCmdsRaw ? allCmdsRaw.collect { it } : []

        List matching = allCmds.findAll { cmd ->
            cmd.stage.text() == stageName && !cmd.start.text().startsWith('1970')
        }

        if(matching.isEmpty()) {
            Set<String> available = (allCmds.collect { it.stage.text() } as Set).findAll { it }
            out.println ""
            out.println "No instances found for stage: ${stageName}"
            if(available) {
                out.println ""
                out.println "Available stages:"
                available.sort().each { out.println "  - ${it}" }
            }
            out.println ""
            System.exit(1)
        }

        matching = matching.sort { toDate(it.start).time }

        List<List> rows = matching.collect { cmd ->
            long startMs = toDate(cmd.start).time
            long endMs = toDate(cmd.end).time
            long startRel = startMs - pipelineStartTimeMs
            long endRel = endMs - pipelineStartTimeMs
            long durMs = endMs - startMs

            String branch = cmd.branch.text() ?: '-'
            String cores = cmd.resources?.procs?.text() ?: '-'
            String exit = cmd.exitCode.text() ?: '-'
            String content = cmd.content.text() ?: ''
            String commandPreview = content.readLines().find { it.trim() } ?: ''
            commandPreview = commandPreview.trim()
            if(commandPreview.size() > 50)
                commandPreview = commandPreview[0..46] + '...'

            String timing = formatTimingBar(startRel, endRel, pipelineTotalMs, 60)

            return [branch, formatTimeSpan(startRel), formatTimeSpan(durMs), cores, exit, timing, commandPreview]
        }

        out.println ""
        out.println " Stage: ${stageName} — ${matching.size()} instance${matching.size() == 1 ? '' : 's'}"
        out.println ""

        Utils.table(["Branch","Start","Duration","Cores","Exit","Timing","Command"], rows, indent:1)
    }
}
