package bpipe.cmd

import bpipe.Utils;
import bpipe.agent.PipelineInfo
import groovy.util.logging.Log;;

@Log
abstract class BpipeCommand {
    
    String commandLine
    
    List<String> args = []
    
    File COMMAND_TMP = new File(System.getProperty("user.home") + "/.bpipedb/commandtmp")
    
    BpipeCommand(String commandLine, List<String> args) {
        this.commandLine = commandLine
        this.args = args
    }
    
    abstract void run(PrintStream out);
    
    String shellExecute(PipelineInfo pipelineInfo) {
        
        // Write the command to tmp directory
        if(!COMMAND_TMP.exists())
            COMMAND_TMP.mkdirs()
        
        String command = "/Users/simon/bpipe/bin/bpipe $commandLine"
        if(args) {
            command = "$command " + args.join(" ")
        }
        
        File tmpFile = new File(COMMAND_TMP, Utils.sha1(command) + ".sh")
        tmpFile.setExecutable(true)
        
        """chmod u+rx $tmpFile.absolutePath""".execute()
        
        log.info("Executing command [$command] via temp file $tmpFile in directory $pipelineInfo.path")
        
        tmpFile.text = command + "\n"
        
        Process p =
            new ProcessBuilder("bash", "-c", tmpFile.absolutePath)
                                .directory(new File(pipelineInfo.path))
                                .start()
                                
        String output                                
        Utils.withStreams(p) {
            StringBuilder out = new StringBuilder()
            StringBuilder err = new StringBuilder()
            p.waitForProcessOutput(out, err)
            int exitValue = p.waitFor()
            if(exitValue != 0) {
                throw new Exception("Failed to start command:\n\n$command\n\nOutput: " + out.toString() + 
                                    "\n--- stderr:\n"+err.toString()+ "\n")
            }
            output = out.toString().trim()
            log.info "Executed command with output: " + output
        }
        return output
    }
}
