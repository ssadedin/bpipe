package bpipe.agent

import java.util.logging.Logger

import bpipe.cmd.BpipeCommand
import bpipe.cmd.RunPipelineCommand
import bpipe.worx.WorxConnection
import bpipe.Utils

class AgentCommandRunner implements Runnable {
    
    private static Logger log = Logger.getLogger("AgentCommandRunner")
    
    WorxConnection worx
    
    Long worxCommandId
    
    BpipeCommand command
    
    public AgentCommandRunner(WorxConnection worx, Long worxCommandId, BpipeCommand command) {
        super();
        this.worx = worx
        this.worxCommandId = worxCommandId;
        this.command = command;
    }
    
    @Override
    public void run() {
        
        try {
            executeAndRespond(worxCommandId) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream()
                Writer out = new WorxStreamingPrintStream(this.worxCommandId, new BufferedWriter(bos.newWriter(), 512), worx)
                if(command instanceof RunPipelineCommand) {
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
            worx.close()
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
