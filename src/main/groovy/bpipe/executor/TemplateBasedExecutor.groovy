package bpipe.executor

import bpipe.Command
import bpipe.PipelineError
import bpipe.Utils;
import groovy.text.SimpleTemplateEngine
import groovy.util.logging.Log;

@Log
class TemplateBasedExecutor {
    
    protected Map config;

    protected String id;

    protected String name;

    /* The command to execute through SGE 'qsub' command */
    protected String cmd;

    protected String jobDir;

    /* Mark the job as stopped by the user */
    protected boolean stopped

    /* The ID of the job as returned by the JOB scheduler */
    protected String commandId;

    /** Command object - only used for updating status */
    Command command

    protected static String CMD_EXIT_FILENAME = "cmd.exit"

    protected static String CMD_FILENAME = "cmd_run.sh"
    
    protected static String CMD_SCRIPT_FILENAME = "cmd.sh"

    protected static String CMD_OUT_FILENAME = "cmd.out"

    protected static String CMD_ERR_FILENAME = "cmd.err"
    
    /**
     * Populate and execute / render a template for the job script based on
     * the given command and, and then execute submit
     * 
     * @param submitCmd             the command to execute to submit the job to the queuing 
     *                              system, for example "qsub" or "bsub". Essential arguments
     *                              should be included, but where possible job configuration should be
     *                              applied by implementing it in the template as this is more
     *                              customisable.
     * @param cmd                   the command to be submitted to the queuing system (the actual 
     *                              job itself)
     * @param defaultJobTemplate    the default name of the template to use to render the job.
     *                              This can be overridden by the user via the jobTemplate 
     *                              configuration value by the user.
     */
    void submitJobTemplate(String submitCmd, String cmd, String defaultJobTemplate, Appendable outputLog, Appendable errorLog) {
        
        def cmdScript = new File(jobDir, CMD_FILENAME)
        cmdScript.text = cmd
        
        String jobTemplateFile = defaultJobTemplate
        if(config?.jobTemplate) {
            if(config.jobTemplate instanceof Closure) {
                jobTemplateFile = String.valueOf(config.jobTemplate(config))
            }
            else 
                jobTemplateFile = String.valueOf(config.jobTemplate)
        }
        
        Integer memoryMB = null
        if(config?.memory && (config?.memory instanceof String)) {
            String mem = config.memory.toLowerCase()
            Integer memInt = mem.replaceAll("[^0-9]", "").toInteger()
            if(mem.endsWith("mb")) {
                memoryMB = memInt
            }
            else
            if(mem.endsWith("gb")) {
                memoryMB = memInt * 1024
            }
        }
        
        File commandTemplate = bpipe.ReportGenerator.resolveTemplateFile(jobTemplateFile)
        
        // Create '.cmd.sh' wrapper used by the 'qsub' command
        def cmdWrapperScript = new File(jobDir, CMD_SCRIPT_FILENAME)
            
        log.info "Generating output from command template ${commandTemplate.absolutePath} to $cmdWrapperScript.absolutePath"
        
        SimpleTemplateEngine e  = new SimpleTemplateEngine()
        def template = commandTemplate.withReader { r ->
            def template = e.createTemplate(r).make(config + [
                config : config, 
                memoryMB : memoryMB,
                cmd : cmd,
                name : name,
                jobDir : jobDir,
                CMD_FILENAME : cmdScript,
                CMD_OUT_FILENAME : CMD_OUT_FILENAME,
                CMD_ERR_FILENAME : CMD_ERR_FILENAME,
                CMD_EXIT_FILENAME : CMD_EXIT_FILENAME
            ])
        }
        
        // Write the template output to the file
        cmdWrapperScript.text = template.toString() 

        log.info "Starting command: '${submitCmd}'"

        ProcessBuilder pb = new ProcessBuilder("bash", "-c", submitCmd)
        Process p = pb.start()
        Utils.withStreams(p) {
            StringBuilder out = new StringBuilder()
            StringBuilder err = new StringBuilder()
            p.waitForProcessOutput(out, err)
            int exitValue = p.waitFor()
            if(exitValue != 0) {
                reportStartError(submitCmd, out,err,exitValue)
                throw new PipelineError("Failed to start command:\n\n$cmd")
            }
            this.commandId = out.toString().trim()
            if(this.commandId.isEmpty())
                throw new PipelineError("Job runner ${this.class.name} failed to return a job id despite reporting success exit code for command:\n\n$submitCmd\n\nRaw output was:[" + out.toString() + "]")

            log.info "Started command with id $commandId"
        }

        // After starting the process, we launch a background thread that waits for the error
        // and output files to appear and then forward those inputs
        forward("$jobDir/$CMD_OUT_FILENAME", outputLog)
        forward("$jobDir/$CMD_ERR_FILENAME", errorLog)
    }

    void reportStartError(String cmd, def out, def err, int exitValue) {
        log.severe "Error starting custom command using command line: " + cmd
        System.err << "\nFailed to execute command using command line: $cmd\n\nReturned exit value $exitValue\n\nOutput:\n\n$out\n\n$err"
    }
}
