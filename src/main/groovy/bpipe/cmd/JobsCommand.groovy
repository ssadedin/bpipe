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

import javax.security.auth.Subject.SecureSet
import javax.swing.text.rtf.RTFGenerator

import bpipe.Command
import bpipe.CommandManager;
import bpipe.Config
import bpipe.ExecutorFactory
import bpipe.ExecutorPool
import bpipe.PooledExecutor
import bpipe.Utils
import groovy.time.TimeCategory
import groovy.time.TimeDuration
import groovy.transform.CompileStatic;
import groovy.util.logging.Log;
import groovyx.gpars.GParsPool

import static org.fusesource.jansi.Ansi.*
import static org.fusesource.jansi.Ansi.Color.*
import org.fusesource.jansi.AnsiConsole

/**
 * Information about a Bpipe Job
 * 
 * @author simon.sadedin
 */
class JobInfo {

    String jobDate
    
    String pid
    
    File jobDir
    
    Date startTime
    
    Date finishTime
    
    String commandName
    
    String runningState 
    
    TimeDuration getTimeSpan() {
        return finishTime && startTime ?  TimeCategory.minus(finishTime, startTime) : null
    }
    
    List toList() {
        return [ jobDate, pid, jobDir, getTimeSpan(), commandName, runningState ]
    }
}

/**
 * Prints out a list of running Bpipe pipelines
 * 
 * @author Simon Sadedin
 */
@Log
class JobsCommand extends BpipeCommand {
    
    public JobsCommand(List<String> args) {
        super("jobs", args);
        cli.usage = "bpipe jobs <options>"
        cli.with {
            all 'Show completed  as well as running jobs'
            watch 'Show continuously updated display'
            sleep 'Sleep time when watching continuously', args:1
            h  'Show help', longOpt: 'help'
        }
        parse()
        
        if(opts.h) {
            cli.usage()
            System.exit(0)
        }
    }

    @Override
    public void run(Writer out) {
        
        AnsiConsole.systemInstall();
        
        Config.config["mode"] = "jobs"
        
        File homeDir = new File(System.properties["user.home"])
        File bpipeDbDir = new File(homeDir, ".bpipedb")
        File jobsDir = new File(bpipeDbDir, "jobs")
        File completedDir = new File(bpipeDbDir, "completed") 
        
        long sleepTime = (opts.sleepTime ?: "10000").toLong()
        long maxAgeMs = 24 * 60 * 60 * 1000
        if(opts.all) {
            maxAgeMs = Long.MAX_VALUE
        }
        
        // There's no need to query completed jobs repeatedly, so we 
        // just do that at the start
        
        List<JobInfo> completedJobs = getJobs(completedDir,maxAgeMs, false)
        
        while(true) {
            
            List<JobInfo> jobRows = getJobs(jobsDir, Long.MAX_VALUE)
            
            jobRows.addAll(completedJobs)
            
            jobRows.sort { row -> -row.startTime.time }
            
            if(opts.watch) {
                print(ansi().eraseScreen().cursor(0, 0))
                print(ansi().bold())
                println(new Date().toString())
                print(ansi().reset())
            }
            
            println ""
            if(jobRows.isEmpty()) {
                if(opts.all) {
                    println "\nNo jobs found\n"
                }
                else
                    println "\nNo currently running jobs\n"
            }
            else {
                Utils.table(["Date", "PID", "Directory", "Run Time", "Stage","State"], jobRows*.toList(), 
                render: [
                    "State": { String val, width -> 
                        if(val.trim() == "Running")  { 
                            print(ansi().fg(GREEN).toString());
                            print(val.padRight(width)); 
                            print(ansi().reset())
                        } else { 
                            print(val.padRight(width))
                        } 
                    }
                ])
            }
            println ""
            
            if(!opts.watch)
                break
                
            Thread.sleep(sleepTime)
        }
        
        AnsiConsole.systemUninstall()
    }

    public List<JobInfo> getJobs(File jobsDir, long maxAgeMs, boolean checkRunning=true) {
        
        File homeDir = new File(System.properties["user.home"])
        File bpipeDbDir = new File(homeDir, ".bpipedb")
        File completedDir = new File(bpipeDbDir, "completed") 
        Date now = new Date()
       
        GParsPool.withPool(4) { 
            return jobsDir.listFiles().collectParallel { File jobFile ->
               
                if(!jobFile.exists()) {
                    jobFile.delete()
                    return null
                } 
                
                if(now.time - maxAgeMs < jobFile.lastModified()) {

                    JobInfo jobInfo = readJobInfo(now, jobFile, checkRunning)
        
                    if(checkRunning && jobInfo.runningState != "Running") {
                        // Remove the symbolic link
                        jobFile.renameTo(new File(completedDir,jobFile.name))
                        return null
                    }        
                    return jobInfo
                }
            }.grep { it != null && it.timeSpan != null } 
            
        }
    }
    
    /**
     * 
     * @param jobFile
     * @return
     */
    JobInfo readJobInfo(Date now, final File jobFile, boolean checkRunning) {

        String pid = jobFile.name

        boolean isRunning = checkRunning && Utils.isProcessRunning(pid)
        
        File jobDir = jobFile.canonicalFile.parentFile.parentFile.parentFile

        Command cmd = isRunning ? getLastCommand(jobDir) : null
        
        
        long finishTimeMs = isRunning ? now.time : new File(jobDir,".bpipe/results/${pid}.xml")?.lastModified() 
       
        return new JobInfo(
            jobDate: new Date(jobFile.lastModified()).format('YYYY-MM-dd'),
            pid: pid, 
            jobDir: jobDir,
            startTime: new Date(jobFile.lastModified()),
            finishTime: finishTimeMs ? new Date(finishTimeMs) : null,
            commandName: cmd?.name,
            runningState:  isRunning ? "Running" : "Finished"
        )
    }
    
    Command getLastCommand(File jobDir) {
        File lastCommandFile = new File(jobDir,".bpipe/commands").listFiles().grep { it.name.isInteger() }.max { File f -> f.lastModified() }
        
        if(lastCommandFile)
            Command.load(lastCommandFile)
    }
}
