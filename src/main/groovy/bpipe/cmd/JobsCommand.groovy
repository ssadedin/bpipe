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

import javax.swing.colorchooser.CenterLayout

import bpipe.CommandManager;
import bpipe.Config
import bpipe.ExecutorFactory
import bpipe.ExecutorPool
import bpipe.PooledExecutor
import bpipe.Utils
import groovy.transform.CompileStatic;
import groovy.util.logging.Log;

/**
 * Prints out a list of running Bpipe pipelines
 * 
 * @author Simon Sadedin
 */
@Log
class JobsCommand extends BpipeCommand {
    
    public JobsCommand(List<String> args) {
        super("jobs", args);
        cli.with {
            all 'Show completed  as well as running jobs'
        }
        parse()
    }

    @Override
    public void run(PrintStream out) {
        
        Config.config["mode"] = "jobs"
        
        File homeDir = new File(System.properties["user.home"])
        File bpipeDbDir = new File(homeDir, ".bpipedb")
        File jobsDir = new File(bpipeDbDir, "jobs")
        File completedDir = new File(bpipeDbDir, "completed") 
        List<List> jobRows = getJobs(jobsDir)
        
        if(opts.all) {
            jobRows.addAll(getJobs(completedDir,false))
        }
        
        jobRows.sort { row -> -row[2][0].time }
        
        println ""
        if(jobRows.isEmpty()) {
            if(opts.all)
                println "\nNo jobs found\n"
            else
                println "\nNo currently running jobs\n"
        }
        else {
            println Utils.table(["PID", "Directory", "Running Time"], jobRows, format: ["Running Time": "timespan"])
        }
        println ""
    }
    
    public List<List> getJobs(File jobsDir, boolean checkRunning=true) {
        
        File homeDir = new File(System.properties["user.home"])
        File bpipeDbDir = new File(homeDir, ".bpipedb")
        File completedDir = new File(bpipeDbDir, "completed") 
        Date now = new Date()
        
       jobsDir.listFiles().collect { jobFile ->
            
            List<String> lines = jobFile.text.readLines()
            List<String> jobInfo = lines[0].tokenize(":")*.trim()
            
            File jobDir = jobFile.canonicalFile.parentFile.parentFile.parentFile
            
            String pid = jobFile.name
            
            if(!checkRunning || Utils.isProcessRunning(pid)) {
                return [
                    pid, 
                    jobDir,
                    [new Date(jobFile.lastModified()), now]
                ]
            }
            else {
                // Remove the symbolic link
                jobFile.renameTo(new File(completedDir,jobFile.name))
                
                return null
            }
        }.grep { it != null } 
    }
}
