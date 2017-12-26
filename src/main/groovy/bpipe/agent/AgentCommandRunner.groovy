package bpipe.agent

import java.util.concurrent.Semaphore
import java.util.logging.Logger

import bpipe.cmd.BpipeCommand
import bpipe.cmd.ClosurePipelineCommand
import bpipe.cmd.RunPipelineCommand
import bpipe.worx.HttpWorxConnection
import bpipe.worx.WorxConnection
import bpipe.Utils

class AgentCommandRunner implements Runnable {
    
    private static Logger log = Logger.getLogger("AgentCommandRunner")
    
    WorxConnection worx
    
    Long worxCommandId
    
    BpipeCommand command
    
    Exception errorResponse
    
    Semaphore concurrency
    
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
        
        concurrency?.acquire()
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
                }
                else {
                    command.out = out
                    command.shellExecute("bpipe", command.dir)
                }
                return bos.toString()
            }
        }
        finally {
            bpipe.Utils.closeQuietly(worx.close())
            concurrency?.release()
        }
    }
    
    private executeAndRespond(Long id, Closure body) {
        try {
            String result = body()
            worx.sendJson("/commandResult/$id", [
                    command: id,
                    status: "ok",
                    output: result
                ]
            )
           Object response = worx.readResponse()
        } 
        catch (Exception e) {
            String stackTrace = Utils.prettyStackTrace(e)
            log.severe "Command $id failed: " + stackTrace
            worx.sendJson("/commandResult/$id", [
                command: id,
                status: "failed",
                error: stackTrace
            ]) 
        }
    }
}
