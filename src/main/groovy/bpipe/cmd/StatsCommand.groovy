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
        }
        def opts = this.parse()
        
        // hack: need to refactor out to be set in constructor
        this.out = out;
        
        List<File> resultFiles 
        if(opts.all) {
            resultFiles = new File(".bpipe/results").listFiles().grep { it.name.endsWith('.xml') }
        }
        else {
            String pid = this.args ? this.args[0] : this.getLastLocalPID()
            
            if(pid == null) {
                out.println "\nNo Bpipe run could be found in this directory\n"
                System.exit(1)
            }
            
            resultFiles = [getResultFile(pid)]
        }
        
        Closure toDate = { value ->
            Date.parse('yyyy-MM-dd HH:mm:ss', value.text())
        }
        
        Closure formatTimeSpan = { t ->
            
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
        
        long totalTimeMs = doms.collect { dom -> toDate(dom.endDateTime).time - toDate(dom.startDateTime).time }.sum()
        String runTime = formatTimeSpan(totalTimeMs)
        
        out.println ""
        
        out.println(" " + (" Pipeline " + (doms.every { it.succeeded.text() == "true"} ? "Succeeded" : "Failed")).center(Config.config.columns-2,"="))
        out.println(("| Started: " + doms[0].startDateTime.text()).padRight(Config.config.columns-1) + "|")
        out.println(("| Ended: " + doms[-1].endDateTime.text()).padRight(Config.config.columns-1) + "|")
        out.println(("| Run Time: " + runTime).padRight(Config.config.columns-1) + "|")
        out.println (" " + "="*(Config.config.columns-2))
        out.println ""
        

        List<List> stats = doms.collect { dom ->dom.commands.command }.sum().groupBy {  cmdNode ->
            cmdNode.stage.text()
        }.collect { stage, cmds ->
            
            // Failed commands do not accurately reflect the time taken, so
            // only count commands that succeeded
            List succeeded = cmds.grep { cmd ->
                cmd.exitCode.text() == "0"
            }
            
            List<Long> times = succeeded.collect { cmd ->  
                long startTimeMs = toDate(cmd.start).time 
                startTimeMs == 0 ? 0 : toDate(cmd.end).time - startTimeMs
            }
            
            double mean = times.isEmpty() ? 0 : times.sum() / times.size()
            
            [
             stage, 
             cmds.size(), 
             formatTimeSpan(times.min()?.toLong()),
             formatTimeSpan(mean), formatTimeSpan(times.max()), 
             ((times.sum()?:0) / 1000.0).toLong()
            ]
        }
        
        Utils.table(["Stage","Count","Min","Mean","Max", "Weight"], stats, indent:1)
        
        out.println ""
    }

}
