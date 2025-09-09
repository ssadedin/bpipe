package bpipe.agent

import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.concurrent.Semaphore

import java.util.logging.Logger

import bpipe.cmd.BpipeCommand
import bpipe.cmd.ClosurePipelineCommand
import bpipe.cmd.RunPipelineCommand
import bpipe.worx.HttpWorxConnection
import bpipe.worx.WorxConnection
import groovy.transform.CompileStatic
import bpipe.ExecutedProcess
import bpipe.Utils

interface AgentCommandCompletionListener {
    void onFinish(Map result) 
}

@CompileStatic
public class NullOutputStream extends OutputStream {
    @Override
    public void write(int b) throws IOException { }
}

@CompileStatic
class AgentCommandRunner implements Runnable {
    
    private static Logger log = Logger.getLogger("AgentCommandRunner")
    
    WorxConnection worx
    
    Long worxCommandId
    
    BpipeCommand command
    
    Exception errorResponse
    
    Semaphore concurrency
    
    AgentCommandCompletionListener completionListener
    
    String outputMode = 'both'
    
    Closure onRun = null
    
    int runState = 0
    
    /**
     * When waiting for acquisition of a lock, this object will be notified to interrupt
     */
    public static Object waitingForLockObject = new Object()
    
    public AgentCommandRunner(WorxConnection worx, Long worxCommandId, BpipeCommand command, String outputMode, Closure onRun = null) {
        super();
        this.worx = worx
        this.worxCommandId = worxCommandId;
        this.command = command;
        this.outputMode = outputMode
        this.onRun = onRun
    }
    
    /**
     * Responds to the given command with the given exception as an error
     * 
     * @param worx
     * @param worxCommandId
     * @param e
     */
    public AgentCommandRunner(WorxConnection worx, Long worxCommandId, Exception e, String outputMode, Closure onRun = null) {
        super();
        this.worx = worx
        this.worxCommandId = worxCommandId;
        this.errorResponse = e
        this.outputMode = outputMode
        this.onRun = onRun
    }
    
    static long MAX_LOCK_WAIT_TIME_MS = 300000L
    
    @Override
    public void run() {
        
        runState = 1
        
        if(concurrency != null) {
            concurrency.acquire()
            log.info "Gained concurrency permit (${concurrency.availablePermits()} remaining) to run command $worxCommandId"
        }

        // Check for the run.pid file and try to lock it
        checkRunningPipelineLock()
           
        int exitCode = 0
        try {
            executeAndRespond(worxCommandId) {
                
                if(errorResponse)
                    throw errorResponse
                
                boolean isRunCommand = (command instanceof RunPipelineCommand || command instanceof ClosurePipelineCommand)
                String outputMode = isRunCommand ? outputMode : 'reply'

                ByteArrayOutputStream bos = null
                Writer writer = null
                if(outputMode == 'both' || outputMode == 'reply') {
                    bos = new ByteArrayOutputStream()
                    writer = new BufferedWriter(bos.newWriter(), 512)
                }

                Writer out = null
                if(outputMode == 'both' || outputMode == 'stream') {
                    out = new WorxStreamingPrintStream(this.worxCommandId, writer, worx)
                }
                else
                    out = writer

                if(writer==null)
                    writer = new NullOutputStream().newWriter()

                String result = ""
                try {
                    log.info "Command $worxCommandId starting"
                    
                    if(!onRun.is(null))
                        onRun.call()

                    if(command instanceof RunPipelineCommand || command instanceof ClosurePipelineCommand) {
                        RunPipelineCommand rpc = (RunPipelineCommand)command
                        command.out = out

                        command.run(out)
                        
                        if(command instanceof RunPipelineCommand)
                            exitCode = ((RunPipelineCommand)command).result.exitValue
                    }
                    else {
                        command.out = out
                        command.shellExecute("bpipe", command.dir)
                    }
                }
                finally {
                    log.info "Command $worxCommandId finished"
                    Utils.closeQuietly(out)
                    Utils.closeQuietly(writer)
                    Utils.closeQuietly(bos)
                }
                if(bos)
                    result = bos.toString()
                return result
            }
        }
        catch(Exception e) {
            exitCode = 1
            throw e
        }
        finally {
            runState = 2

            bpipe.Utils.closeQuietly(worx.close())
            
            if(concurrency != null) {
                concurrency.release()
                log.info "Released concurrency permit from command $worxCommandId (${concurrency.availablePermits()} now available)"
                synchronized(waitingForLockObject) {
                    waitingForLockObject.notifyAll()
                }
            }
            
            if(this.completionListener != null) {
                log.info "Completion listener set: sending completion notification for command $worxCommandId with exit code $exitCode"
                this.completionListener.onFinish([
                    command: worxCommandId,
                    status: exitCode == 0 ? "ok" : "failed"
                ])
            }
            else {
                log.info "Command $worxCommandId completed with exit code $exitCode (no completion listener set)"
            }
        }
    }

    /**
     * Look for a run.pid file from a running pipeline. If one is found, 
     * test the lock on it to find out if the pipeline is still running. If so,
     * wait with backoff before launching the pipeline
     * 
     * Note: this isn't 100% foolproof; two pipelines could still try to run at the 
     * same time if race condition occurs. It should be very rare, but if it is a concern in that 
     * case the risk can be addressed by running Bpipe in "queue" mode.
     */
    private void checkRunningPipelineLock() {
        if(!(command instanceof RunPipelineCommand)) {
            return
        }

        File runId = new File("$command.dir/.bpipe/lock")
        if(!runId.exists()) {
            return
        }

        log.info "Run id file for pipeline in $command.dir exists: checking lock available ..."
        RandomAccessFile runIdRAF = new RandomAccessFile(runId, "rw")
        FileLock lock = null
        long waitTimeMs = 60000
        try {
            while(true) {
                
                try {
                    lock = runIdRAF.channel.tryLock()
                }
                catch(OverlappingFileLockException olfe) {
                    lock = null
                }
                
                if(lock) {
                    log.info "Successfully acquired lock in $command.dir : continuing to run pipeline"
                    
                    // This is here so that if multiple threads are waiting they all have a chance to reattempt the
                    // file lock, ensuring that multiple threads should not grab the lock
                    Thread.sleep(3000)

                    break
                }

                log.info "Directory $command.dir appears to have a running pipeline: waiting $waitTimeMs to retry"
                synchronized(waitingForLockObject) {
                    waitingForLockObject.wait(waitTimeMs)
                }
                
                waitTimeMs = Math.min(waitTimeMs*2, MAX_LOCK_WAIT_TIME_MS)
            }
        }
        finally {
            if(lock != null)
                lock.close()
        }
    }
    
    @CompileStatic
    private executeAndRespond(Long id, Closure body) {
        
        Map jsonResponse = null
        try {
            String result = body()
            jsonResponse = [
                    command: id,
                    status: "ok",
                    output: result
                ]
           worx.sendJson("/commandResult/$id", jsonResponse)
           Object response = worx.readResponse()
        } 
        catch (Exception e) {
            String stackTrace = Utils.prettyStackTrace(e)
            log.severe "Command $id failed: " + stackTrace
            jsonResponse = [
                command: id,
                status: "failed",
                error: stackTrace
            ]
            worx.sendJson("/commandResult/$id", jsonResponse) 
        }
        
    }
}
