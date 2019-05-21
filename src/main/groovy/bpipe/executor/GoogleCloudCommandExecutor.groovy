package bpipe.executor

import java.util.List

import java.util.Map
import java.util.concurrent.ConcurrentHashMap

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

/**
 * An executor that runs commands using Google Cloud infrastructure
 * <p>
 * Problem: who is responsible for mounting the storage?
 * <p>
 * In theory, executors and storage are separate. For example, you could mount
 * S3 storage inside a GCE VM and then run jobs on the data. So in that view of the world,
 * the executor should not worry about storage and should just assume that Bpipe has sorted
 * it out.
 * <p>
 * However this falls short because in order to *mount* storage, you have to *run a command*
 * on the target VM! so there is a complicated dance:
 * 
 *  <li> executor starts instance
 *  <li> Bpipe mounts storage
 *  <li> executor runs command
 *  <p>
 *  
 *  It's even more complicated because the mechanism by which Bpipe sends commands to the VM
 *  involves writing to shared storage. That is, there has to be a shared directory visible
 *  to both Bpipe and the VM. So if a command can't be sent until there is storage, but there
 *  can't be storage without a command, how do we break this deadlock?
 * 
 * @author simon.sadedin
 */
@Log
@Mixin(ForwardHost)
class GoogleCloudCommandExecutor extends CloudExecutor {
    
    public static final long serialVersionUID = 0L
    
    /**
     * The zone in which this executor's instance is running
     */
    String zone
    
    /**
     * If the command is running, the process representing the SSH command
     * that is executing the remote process.
     */
    transient Process process
    
    /**
     * Set to true after the command is observed to be finished
     */
    transient boolean finished = false
    
    /**
     * The directory that the executor will execute in within the hosted VM instance
     */
    String workingDirectory = 'work'
    
    @Override
    public String status() {
        
        if(acquiring)
            return CommandStatus.WAITING
        else
        if(finished) 
            return CommandStatus.COMPLETE
        else
        if(process != null) {
            try {
                log.info "Checking exit code for ${commandId}"
                this.exitCode = this.process.exitValue()
                this.finished = true
                log.info "Command ${commandId} is finished"
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
            List<String> zoneFlag = ['--zone', zone]
            List<String> statusCommand = (["$sdkHome/bin/gcloud","compute","ssh"] + zoneFlag + ["--command","ps -p `cat $workingDirectory/.bpipe/gcloud/$commandId`",this.instanceId])*.toString()
            ExecutedProcess result = Utils.executeCommand(statusCommand)
            if(result.exitValue == 0) {
                log.info "Probe of $instanceId succeeded: instance is in running state"
                return CommandStatus.RUNNING
            }
            else {
                log.info "Probe of $instanceId failed: instance is not in running state"
                return CommandStatus.COMPLETE
            }
        }
        else
            assert false // I think this shouldn't happen
    }

    @Override
    public void stop() {
        assert this.process != null
        this.process.destroy()
    }

    @Override
    public void cleanup() {
        
        List zoneFlag = zone ?  ['--zone', zone] : []
        
        String sdkHome = getSDKHome()
        List<String> deleteCommand = ["$sdkHome/bin/gcloud","compute","instances","delete"] + zoneFlag + ["--quiet",instanceId]
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
    void acquireInstance(Map config, String image, String id) {
        
        String sdkHome = getSDKHome()
            
        String serviceAccount = config.get('serviceAccount',null)
        if(serviceAccount == null)
            throw new Exception("The Google Cloud executor requires that a service account be attached. Please see documentation for how to setup and create the service account.")
        
        this.instanceId = "bpipe-" + id
        
        List machineTypeFlag = ('machineType' in config) ? ["--machine-type", config.machineType] : []
        
        this.zone = getRegion(config)
        List zoneFlag = ['--zone', zone]
        
        List<String> commandArgs = ["$sdkHome/bin/gcloud", "compute", "instances", 
              "create", this.instanceId,
              "--image", image,
              "--service-account", serviceAccount,
              "--scopes", "https://www.googleapis.com/auth/cloud-platform"
        ] + machineTypeFlag + zoneFlag
        
        // Create the instance
        log.info "Creating google cloud instance from image $image for command $id"
        ExecutedProcess result = Utils.executeCommand(commandArgs)
        if(result.exitValue != 0) 
            throw new PipelineError("Failed to acquire google cloud instance based (image=$image) for command $id: \n\n" + result.out + "\n\n" + result.err)
            
    }

    ExecutedProcess ssh(String cmd, Closure builder=null) {
        
        List zoneFlag = zone ? ['--zone', zone] : []
        
        String sdkHome = getSDKHome()
        
        List<String> sshCommand = ["$sdkHome/bin/gcloud","compute","ssh"] + zoneFlag + ["--command",cmd,this.instanceId]*.toString()
        
        return Utils.executeCommand([throwOnError: true], sshCommand, builder)        
    }
    
    
    void mountStorage(Map config) {
        
        String sdkHome = getSDKHome()
        
        String region = getRegion(config)
        
        log.info "Region for Google Cloud is $region"
        
        List<GoogleCloudStorageLayer> storages = this.makeBuckets(config, region)
        for(GoogleCloudStorageLayer storage in storages) {
            this.mountStorage(storage)
        }
        
        if(storages.size()>0) {
            this.workingDirectory = storages[0].path
        }
    }
    
    void startCommand(Command command, Appendable outputLog, Appendable errorLog) {
        
        assert this.instanceId != null
        
        this.commandId = command.id
        
        String sdkHome = getSDKHome()
        
        File jobDir = this.getJobDir(command.id)
        
        String commandWorkDir = workingDirectory
        
        if(command.processedConfig?.containsKey('workingDirectory')) {
            commandWorkDir = command.processedConfig.workingDirectory
        }
        
        log.info "Working directory for command $command.id is $commandWorkDir"
        
        File cmdFile = new File(jobDir, "cmd.sh")
        cmdFile.text = """
            cd $commandWorkDir || { echo "Unable to change to expected working directory: $workingDirectory"; exit 1; }

            mkdir -p .bpipe/gcloud

            echo \$\$ > .bpipe/gcloud/$command.id

            $command.command
        """.stripIndent()
        
        def zoneFlag = ['--zone', zone]
        def sshCommand = ["$sdkHome/bin/gcloud","compute","ssh"] + zoneFlag + ["--command","bash",this.instanceId]
        
        log.info "Lanching command using: " + sshCommand.join(' ')
        
        File stdOut = new File(jobDir,"cmd.out")
        File stdErr = new File(jobDir,"cmd.err")
        
        ProcessBuilder pb = new ProcessBuilder((List<String>)sshCommand*.toString())
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
                "$sdkHome/bin/gcloud","config","get-value","compute/zone"
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
            if(storageConfig == 'auto') {
                log.info "Storage set to auto: assume bucket and mount exists already"
                return
            }
            log.info "Creating storage $storageConfig"
            GoogleCloudStorageLayer storageLayer = StorageLayer.create(storageConfig)
            storageLayer.makeBucket()
            return storageLayer
        }.findAll { 
            it != null  // happens if storageConfig 'auto'
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
    
    @Override
    public void reconnect(Appendable outputLog, Appendable errorLog) {
        this.startCommand(outputLog, errorLog)
    }

    @CompileStatic
    @Override
    public String localPath(String storageName) {
        // the storage will get mapped to t
        // /home/<user>/<storage name>
        return "/home/${System.properties['user.name']}/$storageName"
    }
    
    transient static ConcurrentHashMap<String, String> mountedStorages = new ConcurrentHashMap()

    @Override
    public void mountStorage(StorageLayer storage) {
        
        if(instanceId == null) {
            log.info "Skipping mount of storage as instance is not created yet. Storage will be auto-mounted at instance creation time"
            return
        }
            
        String storageKey = "$storage.name:$instanceId"
        
        if(mountedStorages.containsKey(storageKey)) {
            log.info "Storage $storageKey is already mounted in instance $instanceId"
            return
        }
        
        String mountCommand = storage.getMountCommand(this)
        
        log.info "Mount command for $storage is $mountCommand"
        
        String sdkHome = getSDKHome()
        
        def zoneFlag = zone ? ['--zone', zone] : []
        List<String> sshCommand = (["$sdkHome/bin/gcloud","compute","ssh"] + zoneFlag + ["--command",mountCommand,this.instanceId])*.toString()        
        
        log.info "Executing command to mount storage $storage in GCE executor: $sshCommand"
        
        Utils.executeCommand(sshCommand, throwOnError:true)
        
        mountedStorages[storageKey] = true
    }

}
