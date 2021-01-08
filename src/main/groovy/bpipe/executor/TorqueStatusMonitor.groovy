/*
 * Copyright (c) MCRI, authors
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
package bpipe.executor

import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import bpipe.Command
import bpipe.CommandStatus
import bpipe.Config
import bpipe.ExecutedProcess
import bpipe.Utils
import groovy.transform.CompileStatic
import groovy.util.logging.Log

class TorqueJobState {
    String jobId
    CommandStatus state
    int exitCode
}

/**
 * Aggregates status polling for Torque jobs so that a single qstat is executed for a registered list of torque jobs rather 
 * than polling each job individually. Relies on <code>qstat -x</code> to return XML formatted job status information.
 * 
 * @author Simon Sadedin
 */
@Log
@Singleton(strict=false)
class TorqueStatusMonitor extends TimerTask {
    
    final static Map<String,String> STATES = [
        Q : CommandStatus.QUEUEING,
        W : CommandStatus.WAITING,
        R : CommandStatus.RUNNING, 
        E : CommandStatus.EXITING,
        C : CommandStatus.COMPLETE
    ]
    
    Map<String, TorqueJobState> jobs = [:]
    
    
    TorqueStatusMonitor() {
        log.info "Starting torque status monitor ..."
        
        long pollIntervalMs = 3000
        if(Config.userConfig.containsKey('torqueStatusMonitorPollInterval')) {
            pollIntervalMs = Config.userConfig.torqueStatusMonitorPollInterval
        }

        bpipe.Poller.getInstance().executor.scheduleAtFixedRate(this, 1000, pollIntervalMs, TimeUnit.MILLISECONDS)
    }
    
    int waitFor(Command command, String jobId) {
        TorqueJobState state = new TorqueJobState(jobId: jobId, state: CommandStatus.UNKNOWN)
        while(true) {
            
            CommandStatus lastState = state.state
            synchronized(jobs) {
                jobs[jobId] = state
                jobs.wait()
                
                if(state.state != lastState) {
                    command.status = state.state.toString()
                    command.save()
                    lastState = state.state
                }
               
                if(state.state == CommandStatus.COMPLETE) {
                    jobs.remove(jobId)
                    return state.exitCode
                }
            }
        }
    }

    @CompileStatic
    @Override
    public void run() {
        synchronized(jobs) {
            try {
                pollJobs()
            }
            catch(Exception e) {
                log.warning("Error occurred in processing torque output: $e")
            }
        }
    }
    
    @CompileStatic
    void pollJobs() {

        def jobIds
        synchronized(jobs) {
            jobIds = this.jobs*.key
        }
        
        if(jobIds.isEmpty())
            return
        
        ExecutedProcess result = Utils.executeCommand((List<Object>)(["qstat","-x"] + jobIds))
        
        /*
         * Output is in the form:
         * <Data><Job><Job_Id>3685393</Job_Id><Job_Name>run_salmon</Job_Name><Job_Owner>joe.blogss@foo</Job_
         *  qstat: Unknown Job Id Error 3685292.mgt.meerkat.mcri.edu.au
         *  qstat: Unknown Job Id Error 12345.mgt.meerkat.mcri.edu.au
         */
        
        List<String> output = result.out.toString().readLines()
        List<String> error = result.err.toString().readLines()
        
        updateStatuses(output, error)
    }

    @CompileStatic
    private updateStatuses(List<String> output, List<String> error) {
        
        output = (List<String>)output.inject([]) { List<String> acc, String value ->
            if(!value.startsWith('<Data>') && value.endsWith('</Data>')) {
                acc[-1] = (String)acc[-1] + value
            }
            else {
                acc.add(value)
            }
            return acc
        }
        
        for(line in output) {
            updateStatus(line)
        }

        for(line in error) {
            updateError(line)
        }
        
        synchronized(jobs) {
            jobs.notifyAll()
        }
    }
    
    void updateStatus(String line) {
        if(line.startsWith("<Data>")) {
            try {
                def xml = new XmlSlurper().parseText(line)
                String jobId = xml.Job.Job_Id.text()

                log.fine "Updating status for jobId ${jobId} based on $line"
                TorqueJobState jobState = jobs[jobId]

                String state = xml.Job.job_state.text()

                CommandStatus newState = STATES.get(state,CommandStatus.UNKNOWN)
                if(newState != jobState.state) {
                    log.info "Job $jobId transitioned state from $jobState.state to $newState due to $line"
                }
                jobState.state = newState
                
                if(xml.Job.exit_status) {
                    String statusText = xml.Job.exit_status.text()
                    if(statusText && statusText.isInteger()) {
                        jobState.exitCode = statusText.toInteger()
                    }
                }
            } 
            catch (Exception e) {
                log.severe("Failed to parse output XML-like output from qsub: " + line + ":" + e.toString())
            }
        }
    }
    
    @CompileStatic
    void updateError(String line) {
        if(line.contains("Unknown Job Id")) {
            Matcher m = (line =~ /Unknown Job Id Error ([0-9]{1,})/)
            if(!m) 
                throw new Exception("Error parsing torque output: unexpected output format: $line")

            String jobId = m.toMatchResult().group(1)
            log.info "Job $jobId set to error state due to transition to unknown job id state: $line"
 
            TorqueJobState jobState = jobs[jobId]
            jobState.state = CommandStatus.COMPLETE
            jobState.exitCode = -1 // error
        }
        else {
            throw new Exception("Error parsing torque output: unexpected error: $line")
        }
    }
}
