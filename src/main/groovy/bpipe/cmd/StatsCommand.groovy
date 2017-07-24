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
        
        String pid = this.args ? this.args[0] : this.getLastLocalPID()
        
        if(pid == null) {
            out.println "\nNo Bpipe run could be found in this directory\n"
            System.exit(1)
        }
        
        Closure toDate = { value ->
            Date.parse('yyyy-MM-dd HH:mm:ss', value.text())
        }
        
        Closure formatTimeSpan = { t ->
           TimeCategory.minus(new Date(t.toLong()), new Date(0)).toString().replaceAll('\\.000 seconds$',' seconds')
        }
        
        File resultFile = getResultFile(pid)
        GPathResult dom = new XmlSlurper().parse(resultFile)
        
        String runTime = formatTimeSpan(toDate(dom.endDateTime).time - toDate(dom.startDateTime).time)
        
        out.println ""
        
        out.println(" " + (" Pipeline " + (dom.succeeded.text() == "true" ? "Succeeded" : "Failed")).center(Config.config.columns-2,"="))
        out.println(("| Started: " + dom.startDateTime.text()).padRight(Config.config.columns-1) + "|")
        out.println(("| Ended: " + dom.endDateTime.text()).padRight(Config.config.columns-1) + "|")
        out.println(("| Run Time: " + runTime).padRight(Config.config.columns-1) + "|")
        out.println (" " + "="*(Config.config.columns-2))
        out.println ""
        

        List<List> stats = dom.commands.command.groupBy {  cmdNode ->
            cmdNode.stage.text()
        }.collect { stage, cmds ->
            List<Long> times = cmds.collect { cmd ->  toDate(cmd.end).time - toDate(cmd.start).time }
            double mean = times.sum() / times.size()
            [stage, cmds.size(), formatTimeSpan(times.min().toLong()), formatTimeSpan(mean), formatTimeSpan(times.max()), (times.sum() / 1000.0).toLong()]
        }
        
        Utils.table(["Stage","Count","Min","Mean","Max", "Weight"], stats, indent:1)
        
        out.println ""
    }

}
