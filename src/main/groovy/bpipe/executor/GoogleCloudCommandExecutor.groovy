package bpipe.executor

import java.util.List
import java.util.Map

import bpipe.Command
import bpipe.CommandStatus
import bpipe.Config
import bpipe.PipelineError
import bpipe.Utils
import bpipe.storage.GoogleCloudStorageLayer
import bpipe.ForwardHost;
import groovy.transform.CompileStatic
import groovy.util.logging.Log

@Log
@Mixin(ForwardHost)
class GoogleCloudCommandExecutor extends CloudExecutor {
    
    public static final long serialVersionUID = 0L
    
    String instanceId
    
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
    
    @Override
    public void start(Map cfg, Command cmd, Appendable outputLog, Appendable errorLog) {
        
        // Acquire my instance
        this.acquireInstance(cfg, cmd)
        
        this.mountStorage(cfg)
        
        // Execute the command via SSH
        this.launchSSH(cmd, outputLog, errorLog)
    }
    
    @Override
    public String status() {
        
        if(this.process == null)
            return CommandStatus.WAITING
            
        if(finished) 
            return command.exitCode
            
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

    @Override
    @CompileStatic
    public int waitFor() {
        
        while(true) {
            CommandStatus status = this.status()
            if(status == CommandStatus.COMPLETE) 
                return command.exitCode;
                
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
        
        return;
        
        String sdkHome = getSDKHome()
        List<String> deleteCommand = ["$sdkHome/bin/gcloud","compute","instances","delete","--quiet",instanceId]
        log.info "Executing delete command: " + deleteCommand.join(' ')
        Map result = Utils.executeCommand(deleteCommand)
        if(result.exitValue != 0) {
            String msg = "Unable to shut down google cloud instance $instanceId : error ${result.exitValue}. Output:\n\n$result.out\n\nStd Err:\n\n$result.err"
            log.severe(msg)
            System.err.println "WARNING: " + msg
        }
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
        
        // Create the instance
        log.info "Creating google cloud instance from image $image for command $cmd.id"
        Map result = Utils.executeCommand(["$sdkHome/bin/gcloud", "compute", "instances", 
              "create", this.instanceId,
              "--image", image,
              "--service-account", serviceAccount,
              "--scopes", "https://www.googleapis.com/auth/cloud-platform"
              ])
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
        
        String bucketName = this.makeBucket(config, region)
        
        log.info "Bucket is $bucketName"
        
        String mountCommand = "mkdir work; gcsfuse --implicit-dirs --stat-cache-ttl 0 --type-cache-ttl 0 $bucketName work"
        
        // Finally mount the storage
        List<String> sshCommand = ["$sdkHome/bin/gcloud","compute","ssh","--command",mountCommand,this.instanceId]*.toString()
        
        Utils.executeCommand(sshCommand, throwOnError: true)
    }
    
    void launchSSH(Command command, Appendable outputLog, Appendable errorLog) {
        
        assert this.instanceId != null
        
        String sdkHome = getSDKHome()
        
        File jobDir = this.getJobDir(command)
        
        File cmdFile = new File(jobDir, "cmd.sh")
        cmdFile.text = 'cd work;\n' + command.command
        
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
        
        forward(stdOut, outputLog)
        forward(stdErr, errorLog)
        
    }
    
    String getRegion(Map config) {
        String sdkHome = getSDKHome()
        String region
        if(config.containsKey('region')) {
            region = config.region
        }
        else {
            region = Utils.executeCommand([
                "$sdkHome/bin/gcloud","config","get-value","compute/region"
            ], throwOnError:true).out?.toString()?.trim()
        }
            
        if(!region)
            throw new Exception("Unable to determine region for Google Cloud Services. Please specify a non-blank value manually in bpipe.config")
        
        return region
    }
    
    final static String BUCKET_ALREADY_EXISTS_ERROR = 'ServiceException: 409'
    
    String makeBucket(Map config, String region) {
        
        String sdkHome = getSDKHome()
        
        // Create a bucket based on the file path of the Bpipe instance
        String storageConfigName = config.get('storage',null)
        if(storageConfigName == null)
            throw new PipelineError("Use of Google Cloud executors requires that the 'storage' configuration element be defined. Please define this to point to Google Cloud storage")
        
        String defaultBucket = GoogleCloudStorageLayer.defaultBucket
        
        String bucketName = Config.userConfig.filesystems.get(storageConfigName,[:]).get('bucket',defaultBucket)
        
        log.info "Work directory is bucket: $bucketName"
        
        List<String> makeBucketCommand = [
            "$sdkHome/bin/gsutil",
            "mb",
            "-c",
            "regional",
             "-l",
             region,
             "gs://$bucketName"
        ]
        
        log.info "Creating bucket for work directory using command: " + makeBucketCommand.join(' ')
        Map result = Utils.executeCommand(makeBucketCommand)
        if(result.exitValue != 0) {
            String stdOut = result.out.toString().trim()
            String stdErr = result.err.toString().trim()
            
            println "stdOut: $stdOut\nstdErr: $stdErr\n"
            
            if(stdOut.contains(BUCKET_ALREADY_EXISTS_ERROR) || stdErr.contains(BUCKET_ALREADY_EXISTS_ERROR)) {
                log.info "Bucket already exists: reusing bucket $bucketName"
            }
            else {
                throw new Exception("Unable to create bucket $bucketName: " + result.out + "\n\n" + result.err)
            }
        }
        
        return bucketName
    }
    
    String getSDKHome() {
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
}
