package bpipe.executor

import java.util.List
import java.util.Map

import bpipe.Command
import bpipe.CommandStatus
import bpipe.Config
import bpipe.ExecutedProcess
import bpipe.PipelineError
import bpipe.Utils
import bpipe.storage.GoogleCloudStorageLayer
import bpipe.storage.StorageLayer
import bpipe.ForwardHost;
import groovy.transform.CompileStatic
import groovy.util.logging.Log

@Log
@Mixin(ForwardHost)
class GoogleCloudCommandExecutor extends CloudExecutor {
    
    public static final long serialVersionUID = 0L
    
    /**
     * The google cloud instance that this executor started to service its command
     */
    String instanceId
    
    /**
     * The command executed by the executor
     */
    String commandId 
    
    /**
     * If the command is running, the process representing the SSH command
     * that is executing the remote process.
     */
    transient Process process
    
    /**
     * The command executed, but only if it was started already
     */
    transient Command command
    
    /**
     * Set to true after the command is observed to be finished
     */
    transient boolean finished = false
    
    boolean acquiring = false
    
    /**
     * The directory that the executor will execute in within the hosted VM instance
     */
    String workingDirectory = 'work'
    
    @Override
    public void start(Map cfg, Command cmd, Appendable outputLog, Appendable errorLog) {
        
        // Acquire my instance
        acquiring = true
        this.acquireInstance(cfg, cmd)
       
        this.mountStorage(cfg)
        
        // Execute the command via SSH
        this.launchSSH(cmd, outputLog, errorLog)
    }
    
    @Override
    public String status() {
        
        if(acquiring)
            return CommandStatus.WAITING
        else
        if(finished) 
            return command.exitCode
        else
        if(process != null) {
            try {
                log.info "Checking exit code for ${command?.id}"
                command.exitCode = this.process.exitValue()
                this.finished = true
                log.info "Command ${command?.id} is finished"
                return CommandStatus.COMPLETE
            }
            catch(IllegalThreadStateException exStillRunning) {
                return CommandStatus.RUNNING
            }
        }
        else
        if(instanceId) {
            // This logic is a little bit special
            // There are two cases: 
            //  - non-pooled - we should always have a process stored so should not arrive here (what about bpipe status?)
            //  - pooled - we arrive here on second execution of bpipe
            //             In that case we expect the command still to be running, as long as the VM is - it runs forever!
            log.info "No local process launched but instance $instanceId found: assume command already running, probing instance"
            String sdkHome = getSDKHome()
            List<String> statusCommand = ["$sdkHome/bin/gcloud","compute","ssh","--command","ps -p `cat $workingDirectory/.bpipe/gcloud/$commandId`",this.instanceId]*.toString()
            ExecutedProcess result = Utils.executeCommand(statusCommand)
            if(result.exitValue == 0) {
                return CommandStatus.RUNNING
            }
            else {
                return CommandStatus.COMPLETE
            }
        }
        else
            assert false // I think this shouldn't happen
    }

    @Override
    @CompileStatic
    public int waitFor() {
        
        while(true) {
            CommandStatus status = this.status()
            if(status == CommandStatus.COMPLETE) 
                // note anomaly: we could come in here after command is lost and process is detached
                // this happens for pooled executor
                return command?.exitCode?:0;
                
            Thread.sleep(5000)
        }
    }

    @Override
    public void stop() {
        assert this.process != null
        this.process.destroy()
    }

    @Override
    public void cleanup() {
        
        String sdkHome = getSDKHome()
        List<String> deleteCommand = ["$sdkHome/bin/gcloud","compute","instances","delete","--quiet",instanceId]
        log.info "Executing delete command: " + deleteCommand.join(' ')
        ExecutedProcess result = Utils.executeCommand(deleteCommand)
        if(result.exitValue != 0) {
            String msg = "Unable to shut down google cloud instance $instanceId : error ${result.exitValue}. Output:\n\n$result.out\n\nStd Err:\n\n$result.err"
            log.severe(msg)
            System.err.println "WARNING: " + msg
        }
        else
            log.info "Successfully shut down Google Cloud instance $instanceId"
    }

    @Override
    public String statusMessage() {
        return "Google Cloud Command [ instance=$instanceId ]: " + command?.id
    }

    @Override
    public List<String> getIgnorableOutputs() {
        // TODO Auto-generated method stub
        return null;
    }
    
    
    /**
     * Attempt to acquire a google cloud instance to run the given command, based on 
     * the given configuration
     * 
     * @param config
     * @param cmd
     */
    void acquireInstance(Map config, Command cmd) {
        
        String sdkHome = getSDKHome()
            
        // Get the instance type
        String image = config.get('image')
        
        String serviceAccount = config.get('serviceAccount',null)
        if(serviceAccount == null)
            throw new Exception("The Google Cloud executor requires that a service account be attached. Please see documentation for how to setup and create the service account.")
        
        this.instanceId = "bpipe-" + cmd.id
        
        List machineTypeFlag = ('machineType' in config) ? ["--machine-type", config.machineType] : []
        
        List<String> commandArgs = ["$sdkHome/bin/gcloud", "compute", "instances", 
              "create", this.instanceId,
              "--image", image,
              "--service-account", serviceAccount,
              "--scopes", "https://www.googleapis.com/auth/cloud-platform"
        ] + machineTypeFlag
        
        // Create the instance
        log.info "Creating google cloud instance from image $image for command $cmd.id"
        ExecutedProcess result = Utils.executeCommand(commandArgs)
        if(result.exitValue != 0) 
            throw new PipelineError("Failed to acquire google cloud instance based (image=$image) for command $cmd.id: \n\n" + result.out + "\n\n" + result.err)
            
        // It can take a small amount of time before the instance can be ssh'd to - downstream 
        // functions will assume that an instance is available for SSH, so it's best to do
        // that check now
        Utils.withRetries(5, backoffBaseTime:3000, message:"Test connect to $instanceId") { 
            canSSH() 
        }
    }
    
    boolean canSSH() {
        String sdkHome = getSDKHome()
        
        List<String> sshCommand = ["$sdkHome/bin/gcloud","compute","ssh","--command","true",this.instanceId]*.toString()
        
        Utils.executeCommand(sshCommand, throwOnError: true)        
    }
    
    void mountStorage(Map config) {
        
        String sdkHome = getSDKHome()
        
        String region = getRegion(config)
        
        log.info "Region for Google Cloud is $region"
        
        List<GoogleCloudStorageLayer> storages = this.makeBuckets(config, region)
        for(GoogleCloudStorageLayer storage in storages) {
            storage.mount(this)
        }
        
        if(storages.size()>0) {
            this.workingDirectory = storages[0].path
        }
    }
    
    void launchSSH(Command command, Appendable outputLog, Appendable errorLog) {
        
        assert this.instanceId != null
        
        this.commandId = command.id
        
        String sdkHome = getSDKHome()
        
        File jobDir = this.getJobDir(command)
        
        File cmdFile = new File(jobDir, "cmd.sh")
        cmdFile.text = """
            cd $workingDirectory || { echo "Unable to change to expected working directory: $workingDirectory"; exit 1; }

            mkdir -p .bpipe/gcloud

            echo \$\$ > .bpipe/gcloud/$command.id

            $command.command
        """.stripIndent()
        
        List<String> sshCommand = ["$sdkHome/bin/gcloud","compute","ssh","--command","bash",this.instanceId]*.toString()
        
        log.info "Lanching command using: " + sshCommand.join(' ')
        
        File stdOut = new File(jobDir,"cmd.out")
        File stdErr = new File(jobDir,"cmd.err")
        
        ProcessBuilder pb = new ProcessBuilder(sshCommand)
        pb.redirectOutput(stdOut)
          .redirectError(stdErr)
          .redirectInput(cmdFile)
        
        this.command = command
        this.process = pb.start()
        this.acquiring = false
        
        forward(stdOut, outputLog)
        forward(stdErr, errorLog)
        
    }
    
    static String getRegion(Map config) {
        String sdkHome = getSDKHome()
        String region
        if(config.containsKey('region')) {
            log.info "Using region from configuration"
            region = config.region
        }
        else {
            log.info "Probing region using gcloud command"
            region = Utils.executeCommand([
                "$sdkHome/bin/gcloud","config","get-value","compute/region"
            ], throwOnError:true).out?.toString()?.trim()
        }
            
        if(!region)
            throw new Exception("Unable to determine region for Google Cloud Services. Please specify a non-blank value manually in bpipe.config")
        
        return region
    }
    
    final static String BUCKET_ALREADY_EXISTS_ERROR = 'ServiceException: 409'
    
    List<GoogleCloudStorageLayer> makeBuckets(Map config, String region) {
        
        String sdkHome = getSDKHome()
        
        // Create a bucket based on the file path of the Bpipe instance
        def storageConfigs = config.get('storage',null)
        if(storageConfigs == null)
            throw new PipelineError("Use of Google Cloud executors requires that the 'storage' configuration element be defined. Please define this to point to Google Cloud storage")
            
        if(storageConfigs instanceof String) {
            storageConfigs = storageConfigs.tokenize(',')*.trim()
        }
        
        log.info "Storages configured are $storageConfigs"
        
        List<GoogleCloudStorageLayer> storages = storageConfigs.collect { String storageConfig ->
            log.info "Creating storage $storageConfig"
            GoogleCloudStorageLayer storageLayer = StorageLayer.create(storageConfig)
            storageLayer.makeBucket()
            return storageLayer
        }
        
        return storages 
    }
    
    static String getSDKHome() {
        ConfigObject gcloud = Config.userConfig.gcloud
        
        String sdkHome = gcloud.get('sdk',null)
        if(!sdkHome)
            throw new PipelineError("Please define the location of the Google Cloud SDK in your bpipe.config of .bpipeconfig, eg: \n\n gcloud { sdk='/path/to/sdk' }" )
        
        return sdkHome
    }
    
    File getJobDir(Command command) {
        String jobDir = ".bpipe/commandtmp/$command.id"
		File jobDirFile = new File(jobDir)
        if(!jobDirFile.exists())
		    jobDirFile.mkdirs() 
        return jobDirFile
    }

    @Override
    public void reconnect(Appendable outputLog, Appendable errorLog) {
        this.launchSSH(outputLog, errorLog)
    }

    @CompileStatic
    @Override
    public String localPath(String storageName) {
        // the storage will get mapped to t
        // /home/<user>/<storage name>
        return "/home/${System.properties['user.name']}/$storageName"
    }
}
