package bpipe.agent

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
class AgentCommandRunner implements Runnable {
    
    private static Logger log = Logger.getLogger("AgentCommandRunner")
    
    WorxConnection worx
    
    Long worxCommandId
    
    BpipeCommand command
    
    Exception errorResponse
    
    Semaphore concurrency
    
    AgentCommandCompletionListener completionListener
    
    public AgentCommandRunner(WorxConnection worx, Long worxCommandId, BpipeCommand command) {
        super();
        this.worx = worx
        this.worxCommandId = worxCommandId;
        this.command = command;
    }
    
    /**
     * Responds to the given command with the given exception as an error
     * 
     * @param worx
     * @param worxCommandId
     * @param e
     */
    public AgentCommandRunner(WorxConnection worx, Long worxCommandId, Exception e) {
        super();
        this.worx = worx
        this.worxCommandId = worxCommandId;
        this.errorResponse = e
    }
    
    @Override
    public void run() {
        
        if(concurrency != null) {
            concurrency.acquire()
            log.info "Gained concurrency permit (${concurrency.availablePermits()} remaining) to run command $worxCommandId"
        }

        int exitCode = 0
        try {
            executeAndRespond(worxCommandId) {
                
                if(errorResponse)
                    throw errorResponse
                
                ByteArrayOutputStream bos = new ByteArrayOutputStream()
                Writer out = new WorxStreamingPrintStream(this.worxCommandId, new BufferedWriter(bos.newWriter(), 512), worx)
                if(command instanceof RunPipelineCommand || command instanceof ClosurePipelineCommand) {
                    command.out = out
                    command.run(out)
                    bos.toString()
                    if(command instanceof RunPipelineCommand)
                        exitCode = command.result.exitValue
                }
                else {
                    command.out = out
                    command.shellExecute("bpipe", command.dir)
                }
                return bos.toString()
            }
        }
        catch(Exception e) {
            exitCode = 1
            throw e
        }
        finally {
            bpipe.Utils.closeQuietly(worx.close())
            
            if(concurrency != null) {
                concurrency.release()
                log.info "Released concurrency permit from command $worxCommandId (${concurrency.availablePermits()} now available)"
            }
            
            if(this.completionListener != null) {
                log.info "Completion listener set: sending completion notification"
                this.completionListener.onFinish([
                    command: worxCommandId,
                    status: exitCode == 0 ? "ok" : "failed"
                ])
            }
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
