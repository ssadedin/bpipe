package bpipe.executor

import java.nio.file.Path
import java.util.concurrent.Semaphore

import bpipe.Command
import bpipe.CommandStatus
import bpipe.Config
import bpipe.ExecutedProcess
import bpipe.ForwardHost
import bpipe.PipelineError
import bpipe.Runner
import bpipe.Utils
import bpipe.storage.StorageLayer
import groovy.transform.CompileStatic
import groovy.util.logging.Log

/**
 * An executor that runs commands by ssh'ing to a remote server to run them.
 * 
 * A number of assumptions are made, the key being that the remote server has access
 * to a shared file system with the source host that includes the working directory
 * in which Bpipe is running and all the pipeline files are stored.
 * 
 * @author simon.sadedin
 */
@Log
class SSHCommandExecutor implements CommandExecutor, ForwardHost {
    
    /**
     * Id to distinguish separate pipelines
     */
    String pipelineId
    
    /**
     * The command executed by the executor
     */
    String commandId 
    
    /**
     * The exit code for the command run by this executor, if any
     */
    Integer exitCode = null

    /**
     * The command executed, but only if it was started already
     */
    transient Command command

    /**
     * The command that was launched
     */
    String runningCommand

    /**
     * if a command is executing on the remote instance, its process id is stored here
     */
    String processId

    /**
     * The SSH user that should be used for executing commands
     */
    String user 

    /**
     * The public DNS hostname of the instance, once acquired
     */
    String hostname
    
    /**
     * Directory in which the command is running (on remote server)
     */
    String workingDirectory

    /**
     * Set to true after the command is observed to be finished
     */
    boolean finished = false
    
    /**
     * Cached arguments to be supplied to SSH for specifying key pair to use
     */
    protected List<String> keypairArgs

    @Override
    public void start(Map inputCfg, Command cmd, Appendable outputLog, Appendable errorLog) {
        
        this.pipelineId = Config.config.pid
        
        this.commandId = cmd.id
        this.command = cmd
        this.runningCommand = command.command
        this.command.save()
        
        // Inherit any default ssh_executor settings
        if(!cmd.processedConfig.containsKey('ssh_executor')) {
            cmd.processedConfig.ssh_executor
        }
        
        inputCfg.ssh_executor = (inputCfg.ssh_executor?:[:]) + (cmd.processedConfig.ssh_executor?:[:]) + (Config.userConfig.ssh_executor?:[:])
        
        this.user = cmd.processedConfig.ssh_executor?.user
        this.hostname = cmd.processedConfig.ssh_executor?.hostname
        
        if(!user)
            throw new PipelineError("user property must be specified for ssh_executor for command $cmd.name")
        
        if(!hostname)
            throw new PipelineError("hostname property must be specified for ssh_executor for command $cmd.name")

        File jobDir = this.getJobDir()
        
        String commandWorkDir = Runner.runDirectory
        
        // When files are mirrored to the cloud instance, the working directory should be the
        // same as the local host
        if(command.processedConfig.getOrDefault('transfer',false)) {
            commandWorkDir = Runner.runDirectory
        }
        
        if(command.processedConfig.containsKey('workingDirectory')) {
            commandWorkDir = command.processedConfig.workingDirectory
        }
        this.workingDirectory = commandWorkDir
        
        log.info "Working directory for command $command.id is $commandWorkDir"
        
        List<String> shell = command.shell ?: ['bash']

        File cmdFile = new File(jobDir, "cmd.sh")
        String cmdText = 
        """
            mkdir -p ${new File(exitFile).absoluteFile.parentFile.path}

            cd $commandWorkDir || { echo "Unable to change to expected working directory: $commandWorkDir > /dev/stderr"; exit 1; }

            echo \$\$ | tee ${getPipelineTmpDir()}/$command.id

            nohup ${shell.join(' ')} <<'BPIPEEOF' > ${remoteOutputPath} 2> ${remoteErrorPath} &
        """.stripIndent() +
            command.command.stripIndent() +
        """
            echo \$? > ${exitFile}

            BPIPEEOF
        """.stripIndent()
        
        // sudo mkdir -p $commandWorkDir && sudo chown \$USER $commandWorkDir
        
        cmdFile.text = cmdText
        
        log.info "Executing SSH wrapper command:\n\n=====\n$cmdText\n=======\n"
        
        this.keypairArgs = cmd.processedConfig.ssh_executor?.keypair ? ["-i", inputCfg.ssh_executor?.keypair] : []
        
        List sshCommand = ["ssh","-oStrictHostKeyChecking=no", *keypairArgs, this.user + '@' + this.hostname,"bash"]
        
        log.info "Launching command saved in $cmdFile using: " + sshCommand.join(' ')
        
        ExecutedProcess proc = Utils.executeCommand([:], sshCommand) {
            redirectInput(cmdFile)
        }
        
        log.info "Prcess launched, waiting for process id"
        
        waitForProcessIdInOutput(proc)
       
        this.reconnect(outputLog, errorLog)        
    }

    @CompileStatic
    public int waitFor() {
        
        while(true) {
            CommandStatus status = this.status()
            
            if(this.command != null) { // defensively, command is transient so will not be there if deserialised
                this.command.status = status
            }
            
            if(status == CommandStatus.COMPLETE) { 
                
                this.stopForwarding()

                return exitCode
            }
                
            Thread.sleep(5000)
        }
    }
   
    public String status() {
        
        if(finished) 
            return CommandStatus.COMPLETE
        else
        if(processId == null) {
            return CommandStatus.WAITING
        }
        else
        if(processId != null) {
            log.info "Checking Exit code for ${commandId} using exit file $exitFile"
            try {
                String absExitFilePath = "$workingDirectory/$exitFile"
                // TODO: decide whether to hoist ssh function from AWS executor, or inline it
                ExecutedProcess probe = ssh("date > /tmp/${commandId}.bpipecheck ;  if [ -e ${absExitFilePath} ]; then exit \$(head -n 1 ${absExitFilePath}); fi; exit 192;", 
                    execOptions: [throwOnError:false, out: System.out, err: System.err, timeout: 10000]
                )
                
                String probeOutput
                if(probe.timedOut || probe.exitValue == 192) {
                    probeOutput = "running"
                }
                else {
                    probeOutput = probe.exitValue
                }

                log.info "Probe output from command $commandId: [$probeOutput]"
                if(probeOutput == "running") {
                    return CommandStatus.RUNNING
                }
                else
                if(probeOutput.isInteger()) {
                    this.exitCode = probeOutput.toInteger() 
                    log.info "Probe of $this returned exit code $exitCode"
                    this.finished = true
                    cleanup()
                    return CommandStatus.COMPLETE
                }
            }
            catch(Exception e) {
                this.finished = true
                log.info "Error in probe: treating command ${commandId} as finished due to $e"
                this.exitCode = -1
                cleanup()
                return CommandStatus.COMPLETE
            }
        }
        else
            assert false // I think this shouldn't happen
    }
    
    @CompileStatic
    public ExecutedProcess ssh(Map options=[:], String cmd, Closure builder=null) {
        
        assert hostname != null && hostname != ""
        
        List sshOptions = []
        if(options?.timeout)
            sshOptions.add("-oConnectTimeout=10")

        if(options.verbose) {
            sshOptions.add("-vvv")
        }
        sshOptions.addAll(keypairArgs)
        
        List<String> sshCommand = ["ssh","-oStrictHostKeyChecking=no", *sshOptions, user + '@' +hostname,cmd]*.toString()
        
        Map execOptions = [:]
        if(options.commandTimeout)
            execOptions.timeout = options.commandTimeout
           
        if(options.execOptions) {
            execOptions += (Map)options.execOptions
        } 

        return Utils.executeCommand((List<Object>)sshCommand, throwOnError: true, *:execOptions)
    }

    @Override
    public void stop() {
    }

    @Override
    public void cleanup() {
    }

    @Override
    public Semaphore getLaunchLock() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String localPath(String storageName) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void mountStorage(StorageLayer storage) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public String statusMessage() {
        return "${runningCommand?:'pending command'} running via SSH instance on $hostname";
    }

    @Override
    public List<String> getIgnorableOutputs() {
        return null;
    }

    @CompileStatic
    File getJobDir() {
        String jobDir = ".bpipe/commandtmp/$commandId"
		File jobDirFile = new File(jobDir)
        if(!jobDirFile.exists())
		    jobDirFile.mkdirs() 
        return jobDirFile
    }
    
    @CompileStatic
    File getPipelineTmpDir() {
        return this.getJobDir()
    }
    
    @CompileStatic
    String getExitFile() {
       new File(getJobDir(), "${commandId}.exit").path
    }
    
    @CompileStatic
    String getRemoteOutputPath() {
       new File(getJobDir(), "${commandId}.out").path
    }
    
    @CompileStatic
    String getRemoteErrorPath() {
       new File(getJobDir(), "${commandId}.err").path
    }
    
    @CompileStatic
    private void waitForProcessIdInOutput(final ExecutedProcess proc) {
        while(true) {
            log.info "Waiting for process id for command $commandId"
            if(!proc.out.toString().isEmpty()) {
                Thread.sleep(100)
                this.processId = proc.out.toString().trim().toInteger()
                log.info "Observed remote process id $processId for command $commandId"
                break
            }
            Thread.sleep(500)
        }
    }
    
    public void reconnect(Appendable outputLog, Appendable errorLog) {
        
        File jobDir = this.getJobDir()
        
        File stdOut = new File(jobDir,"cmd.out")
        File stdErr = new File(jobDir,"cmd.err")
        
        [
            [ remote: remoteOutputPath,  local: stdOut, logger: outputLog ],
            [ remote: remoteErrorPath,  local: stdErr, logger: errorLog ],
        ].each { conn ->
            
            List<String> sshCommand = ["ssh","-oStrictHostKeyChecking=no", *keypairArgs, user + '@' +hostname, "cd $workingDirectory && tail -f -n 0 -c +${conn.local.length()} -n 1 ${conn.remote}"]*.toString()

            log.info "Connecting logs to $conn.local via ${sshCommand.join(' ')}"
            ProcessBuilder pb = new ProcessBuilder(sshCommand)
            pb.redirectOutput(conn.local)
              .redirectError(conn.local)

            def logsProcess = pb.start()
            forward(conn.local, conn.logger) 
        }
    }
     
 }
