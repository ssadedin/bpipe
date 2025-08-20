/*
 * Copyright (c) 2019 MCRI, authors
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package bpipe.executor

import java.nio.file.Path
import java.util.logging.Logger

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Regions
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.ec2.model.*
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder

import bpipe.Command
import bpipe.CommandStatus
import bpipe.Config
import bpipe.ExecutedProcess
import bpipe.PipelineError
import bpipe.PipelineFile
import bpipe.Runner
import bpipe.Utils
import bpipe.storage.StorageLayer
import groovy.transform.CompileStatic
import groovy.transform.ToString


/**
 * An executor that runs commands by starting AWS EC2 images
 */
@ToString(includeNames=true, excludes=['runningCommand','remoteErrorPath','exitFile'])
class AWSEC2CommandExecutor extends CloudExecutor {
    
    public static final long serialVersionUID = 0L

    static final Logger log = Logger.getLogger('AWSEC2CommandExecutor')
    
    /**
     * Path to pem file containing the key pair to use for SSH access to instances
     * <p>
     * This is a required configuration parameter
     */
    String keypair
    
    /**
     * The public DNS hostname of the instance, once acquired
     */
    String hostname
    
    /**
     * The SSH user that should be used for executing commands
     */
    String user 
    
    /**
     * if a command is executing on the remote instance, its process id is stored here
     */
    String processId
    
    /**
     * Id to distinguish separate pipelines
     */
    String pipelineId
    
    /**
     * Not used yet: the working directory to run commands in on the remote instance
     */
    String workingDirectory = 'work'
    
    /**
     * The command that was launched
     */
    String runningCommand
    
    protected transient AmazonEC2 ec2
    
    protected transient AmazonS3 s3client

    transient List<Process> logsProcesses
    
    boolean autoShutdown = true

    boolean autoStop = false

    boolean imdsv2 = null
    
    /**
     * Mounted storage associated with this executor or null if no storage associated
     */
    StorageLayer storage
    
    public AWSEC2CommandExecutor() {
        super()
    }
    
    @Override
    public void reconnect(Appendable outputLog, Appendable errorLog) {
        
        File jobDir = this.getJobDir(commandId)
        
        File stdOut = new File(jobDir,"cmd.out")
        File stdErr = new File(jobDir,"cmd.err")
        
        [
            [ remote: remoteOutputPath,  local: stdOut, logger: outputLog ],
            [ remote: remoteErrorPath,  local: stdErr, logger: errorLog ],
        ].each { conn ->
            
            List<String> sshCommand = ["ssh","-oStrictHostKeyChecking=no", "-i", keypair, user + '@' +hostname, "tail -f -n 0 -c +${conn.local.length()} -n 1 ${conn.remote}"]*.toString()

            log.info "Connecting logs to $conn.local via ${sshCommand.join(' ')}"
            ProcessBuilder pb = new ProcessBuilder(sshCommand)
            pb.redirectOutput(conn.local)
              .redirectError(conn.local)

            def logsProcess = pb.start()
            forward(conn.local, conn.logger) 
        }
    }
    
    @Override
    public String status() {
        
        if(acquiring)
            return CommandStatus.WAITING
        else
        if(finished) 
            return CommandStatus.COMPLETE
        else
        if(instanceId && processId == null) {
            return CommandStatus.WAITING
        }
        else
        if(processId != null) {
            log.info "Checking Exit code for ${commandId}"
            try {
//                ExecutedProcess probe = ssh(commandTimeout:10000, "bash -c 'if [ -e ${exitFile} ]; then cat ${exitFile}; else echo running;  fi; exit 0;'")
                ExecutedProcess probe = ssh("date > /tmp/${commandId}.bpipecheck ;  if [ -e ${exitFile} ]; then exit \$(head -n 1 ${exitFile}); fi; exit 192;", 
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
    
    void disconnectLogs() {
    }
    
    
    @Override
    public void stop() {
    }
    
    @Override
    public void cleanup() {
        this.logsProcesses?.each { logsProcess ->
            if(logsProcess) {
                logsProcess.destroy()
                logsProcess = null
            }
        }
        
        // Must be done before termination to get cloud output files back
        super.cleanup()
        
        if(autoStop) {
            // by shutting down this way, the instance will live long enough that the user can log in to debug something
            // or otherwise capture the state
            log.info "Stopping instance $instanceId because autoShutdown=$autoShutdown and autoStop=$autoStop"
            this.ssh("sudo shutdown +5")
        }
        else
        if(autoShutdown) {
            if(instanceId) {
                log.info "Terminating instance $instanceId for executor $this"
                
                if(ec2 == null) {
                    this.createClient(this.command.getConfig(this.command.inputs))
                }
                
                ec2.terminateInstances(new TerminateInstancesRequest([instanceId]))
            }
        }
        else {
            log.info "Instance $instanceId will not be terminated because autoShutdown = $autoShutdown"
        }
    }
    
    @Override
    public String localPath(String storageName) {
        return '.';
    }
    
    @Override
    public void mountStorage(StorageLayer storage) {
    }

    @Override
    public String statusMessage() {
        return "${runningCommand?:'pending command'} running on AWSEC2 instance $instanceId ($hostname)";
    }

    @Override
    public List<String> getIgnorableOutputs() {
        // TODO Auto-generated method stub
        return null;
    }
    
    @CompileStatic
    protected void createClient(Map config) {
        
        validateConfig(config)
             
        this.keypair = config.keypair
        this.user = config.user
             
        AWSCredentials credentials = new BasicAWSCredentials(
            (String)config.accessKey,
            (String)config.accessSecret
        );
        
        ec2 = AmazonEC2ClientBuilder
            .standard()
            .withCredentials(new AWSStaticCredentialsProvider(credentials))
            .withRegion(Regions.fromName((String)config.region))
            .build();        
        
          
        s3client = AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(Regions.fromName((String)config.region))
                .build();
    }

    /**
     * Check that the config contains all required keys and where optional sources of information
     * are available (eg: environment variables) apply these to the config object.
     * 
     * @param config    config object to validate
     */
    private void validateConfig(Map config) {
        if(!config.containsKey('accessKey')) {
            if(Config.userConfig.containsKey('accessKey'))
                config.accessKey = Config.userConfig.accessKey
            else
            if(config.containsKey('profile')) {
                bpipe.executor.AWSCredentials credsInstance = bpipe.executor.AWSCredentials.getTheInstance()
                Map keyInfo = credsInstance.keys[config.profile]
                if(keyInfo == null) {
                    throw new PipelineError("Profile $config.profile was not found in the aws credentials file loaded from $credsInstance.credentialsFile")
                }
                config.accessKey = keyInfo.access_key_id
                config.accessSecret = keyInfo.secret_access_key
            }
            else
            if(System.getenv('AWS_ACCESS_KEY_ID'))
                config.accessKey = System.getenv('AWS_ACCESS_KEY_ID')
            else
                throw new Exception('AWSEC2 executor requires the accessKey or profile configuration setting, or to have AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY environment variables set')
        }

        if(!config.containsKey('accessSecret'))  {
            if(Config.userConfig.containsKey('accessSecret'))
                config.accessSecret = Config.userConfig.accessSecret
            else
                if(System.getenv('AWS_SECRET_ACCESS_KEY'))
                    config.accessSecret = System.getenv('AWS_SECRET_ACCESS_KEY')
                else
                    throw new Exception('AWSEC2 executor requires the accessSecret configuration setting')
        }

        if(!config.containsKey('keypair'))
            throw new Exception('AWSEC2 executor requires the keypair configuration setting. Please set this to the path to the PEM file to allow SSH access to your instances')

        if(!config.containsKey('user'))
            throw new Exception('AWSEC2 executor requires the user configuration setting. Please set this to the id of the user that should be used for SSH commands in your image')

        if(!config.containsKey('region')) 
            throw new Exception('AWSEC2 executor requires the region configuration setting. Please set this to the region in which you would like your instances to launch.')            
     }
    
    @CompileStatic
    @Override
    public void acquireInstance(Map config, String image, String id) {
        
        this.autoShutdown = config.getOrDefault('autoShutdown', this.autoShutdown)
        this.autoStop = config.getOrDefault('autoStop', this.autoStop)
        this.imdsv2 = config.getOrDefault('imdsv2', this.imdsv2)
        
        createClient(config)
        
        InstanceType instanceType = InstanceType.T1Micro
        if(config.containsKey('instanceType')) {
            instanceType = InstanceType.fromValue((String)config.instanceType)
        }
        
        
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
        runInstancesRequest.withImageId(image)
                .withInstanceType(instanceType)
                .withMinCount(1)
                .withMaxCount(1)
                .withKeyName(new File(keypair).name.replaceAll('.pem$',''))
                .withTagSpecifications(
                    new TagSpecification(resourceType: ResourceType.Instance.toString())
                        .withTags(new Tag().withKey('Name').withValue("${command.name}-${Config.config.pid}-${command.id}"))
                )

        if(this.imdsv2 != null) {
            runInstancesRequest = runInstancesRequest.withMetadataOptions(
                new InstanceMetadataOptionsRequest()
                    .withHttpTokens(this.imdsv2 ? 'required' : 'optional')
            )
        }
        if(config.containsKey('securityGroup')) {
            runInstancesRequest = runInstancesRequest.withSecurityGroups((String)config.securityGroup)
        }         
        
        if(config.containsKey('instanceProfile'))
            runInstancesRequest.setIamInstanceProfile(new IamInstanceProfileSpecification().withName((String)config.instanceProfile))
            
        def userData = resolveUserData(config)
        if(userData)
            runInstancesRequest = runInstancesRequest.withUserData(userData)

        RunInstancesResult result       

        List<String> retryErrorCodes = [ "InsufficientInstanceCapacity", "VcpuLimitExceeded", "RequestLimitExceeded" ]
        try {
            result = this.ec2.runInstances(runInstancesRequest)
        }
        catch(AmazonEC2Exception ex) {
            if(!(ex.errorCode in retryErrorCodes))
                throw ex
            else
                throw new CapacityTemporarilyUnavailableException("Unable to acquire instance for job $id (image=$image, instanceType=$instanceType) due to $ex.errorCode", ex)
        }

        this.instanceId = result.reservation.instances[0].instanceId
        this.hostname = queryHostName()
        
        log.info "Instance $instanceId started with host name $hostname"
    }
    
    /**
     * Check the given config for elements that would require user data to be set. These include
     * an explicit init script, or alternatively, a walltime which is implemented through a shutdown
     * command that is set as an init script
     * 
     * @param config
     * @return  base 64 encoded user data
     */
    @CompileStatic
    String resolveUserData(Map config) {
        List<String> initScriptParts = []
        if(config.containsKey('walltime')) {
            // Walltime expressed in hh:mm:ss form
            // But we need it in minutes
            List<Integer> timeParts = ((String)config.walltime).tokenize(':')*.toInteger()
            int minutes = timeParts.size()==3 ? (timeParts[0] * 60 + timeParts[1]) : timeParts[0]
            log.info "Calculated $minutes shutdown minutes from walltime $config.walltime"
            initScriptParts.add("shutdown -h +$minutes".toString())
        }
        
        if(config.containsKey('initScript')) {
            initScriptParts.add((String)config['initScript'])
        }
        
        if(initScriptParts) {
            final String script = '#!/bin/bash\n' + initScriptParts.join('\n') + '\n'
            final String userData = script.bytes.encodeBase64()
            log.info "Resolved init script for $command.name as userData:\n\n$script"
            return userData
        }         
        else {
            return null
        }
    }
    
    @CompileStatic
    void connectInstance(Map config) {

        this.autoShutdown = config.getOrDefault('autoShutdown', this.autoShutdown)
        
        createClient(config)

        DescribeInstancesResult dir = describeInstance()
        if(dir.reservations.isEmpty())
            throw new PipelineError("Pre-specified EC2 instance $instanceId is not available / has no reservations in your EC2 account")
            
        List<Instance> instances = (List<Instance>)dir*.reservations*.instances.flatten()

        log.info "Found ${instances.size()} instances for instanceid $instanceId"
        
        Instance instance = instances.find { it.state.name == "running" }
        if(instance == null) {
            instance = instances.find { it.state.name == "stopped" }
            if(!instance) {
                throw new PipelineError("AWS EC2 executor was configured to use instance $instanceId, but this instance does not exist in stopped or running state")
            }
            
            startStoppedInstance(instance.instanceId)
        }

        assert this.instanceId
        this.hostname = queryHostName()
        log.info "Resolved hostname $hostname for instance $instanceId"
    }

    private startStoppedInstance(String instanceId) {
        StartInstancesRequest startInstancesRequest = new StartInstancesRequest()
        startInstancesRequest.setInstanceIds([instanceId])
        StartInstancesResult result = ec2.startInstances(startInstancesRequest);

        Utils.waitWithTimeout(120000) {
            List<Instance> allInstances = (List<Instance>)describeInstance()*.reservations*.instances.flatten()

            Instance theInstance = allInstances.find { it.instanceId == instanceId }

            if(theInstance.state.name == "running") {
                return true
            }

            Thread.sleep(10000)

            log.info("Waiting for instance $instanceId to be in running state (current state = $theInstance.state)")

            return null
        }
    }
    
    @CompileStatic
    protected String queryHostName() {
        String hostname = Utils.withRetries(8) {
            DescribeInstancesResult result = describeInstance();
            for(Reservation reservations : result.getReservations()) {
                Instance instance = reservations.instances.find { it.instanceId == instanceId }
                if(instance.publicDnsName) {
                    return instance.publicDnsName
                }
            }
            
            log.info "Still waiting for $instanceId to have a hostname"
            throw new IllegalStateException('Instance does not have public host name yet')
        }
        
        if(!hostname)
            throw new IllegalStateException('Could not resolve public DNS hostname for instance ' + instanceId)
            
        return hostname
    }

    @CompileStatic
    private DescribeInstancesResult describeInstance() {
        DescribeInstancesRequest request = new DescribeInstancesRequest();
        request.withInstanceIds(instanceId);
        DescribeInstancesResult result = ec2.describeInstances(request)
        return result
    }

    @Override
    public void startCommand(Command command, Appendable outputLog, Appendable errorLog) {
        
        this.pipelineId = Config.config.pid
        
        assert this.instanceId != null
        
        this.commandId = command.id
        
        this.runningCommand = command.command
        
        this.command.save()
        
        File jobDir = this.getJobDir(commandId)
        
        String commandWorkDir = workingDirectory
        
        // When files are mirrored to the cloud instance, the working directory should be the
        // same as the local host
        if(command.processedConfig.getOrDefault('transfer',false)) {
            commandWorkDir = Runner.runDirectory
        }
        
        if(command.processedConfig?.containsKey('workingDirectory')) {
            commandWorkDir = command.processedConfig.workingDirectory
        }
        
        log.info "Working directory for command $command.id is $commandWorkDir"
        
        
        List<String> shell = command.shell ?: ['bash']

        File cmdFile = new File(jobDir, "cmd.sh")
        String cmdText = 
        """
            mkdir -p ${new File(exitFile).absoluteFile.parentFile.path}

            echo \$\$ | tee ${getPipelineTmpDir()}/$command.id

            sudo mkdir -p $commandWorkDir && sudo chown \$USER $commandWorkDir

            cd $commandWorkDir || { echo "Unable to change to expected working directory: $workingDirectory > /dev/stderr"; exit 1; }

            nohup ${shell.join(' ')} <<'BPIPEEOF' > ${remoteOutputPath} 2> ${remoteErrorPath} &
        """.stripIndent() +
            command.command.stripIndent() +
        """
            echo \$? > ${exitFile}

            BPIPEEOF
        """.stripIndent()
        
        cmdFile.text = cmdText
        
        log.info "Executing AWS wrapper command:\n\n=====\n$cmdText\n=======\n"
        
        List sshCommand = ["ssh","-oStrictHostKeyChecking=no", "-i", keypair, this.user + '@' + this.hostname,"bash"]

        log.info "Launching command saved in $cmdFile using: " + sshCommand.join(' ')
        
        ExecutedProcess proc = Utils.executeCommand([:], sshCommand) {
            redirectInput(cmdFile)
        }
        
        waitForProcessIdInOutput(proc)
       
        this.acquiring = false
        
        this.reconnect(outputLog, errorLog)
    }
    
    @CompileStatic
    String getRemoteOutputPath() {
        "/tmp/bpipe-aws/$pipelineId/${commandId}.out"
    }
    
    @CompileStatic
    String getRemoteErrorPath() {
        "/tmp/bpipe-aws/$pipelineId/${commandId}.err"
    }
    
    @CompileStatic
    private void waitForProcessIdInOutput(final ExecutedProcess proc) {
        while(true) {
            if(!proc.out.toString().isEmpty()) {
                Thread.sleep(100)
                this.processId = proc.out.toString().trim().toInteger()
                log.info "Observed remote process id $processId for command $commandId"
                break
            }
            Thread.sleep(500)
        }
    }
    
    @CompileStatic
    String getExitFile() {
       "/tmp/bpipe-aws/$pipelineId/${commandId}.exit"
    }
    
    @Override
    @CompileStatic
    public ExecutedProcess ssh(Map options=[:], String cmd, Closure builder=null) {
        
        assert hostname != null && hostname != ""
        
        List sshOptions = []
        if(options?.timeout)
            sshOptions.add("-oConnectTimeout=10")

        if(options.verbose) {
            sshOptions.add("-vvv")
        }
        
        List<String> sshCommand = ["ssh","-oStrictHostKeyChecking=no", *sshOptions, "-i", keypair, user + '@' +hostname,cmd]*.toString()
        
        Map execOptions = [:]
        if(options.commandTimeout)
            execOptions.timeout = options.commandTimeout
           
        if(options.execOptions) {
            execOptions += (Map)options.execOptions
        } 

        return Utils.executeCommand((List<Object>)sshCommand, throwOnError: true, *:execOptions)
    }
    
    @Override
    @CompileStatic
    public void transferTo(List<PipelineFile> fileList) {

        assert hostname != null && hostname != ""
        
        Map<String,List<PipelineFile>> dirGroups = fileList.groupBy { it.toPath().toFile().absoluteFile.parentFile.absolutePath }
        
        List<String> outputDirs = (List<String>)command.outputs.groupBy {  PipelineFile output ->
            Path outputPath = output.toPath()
            outputPath.toAbsolutePath().parent.toString()
        }*.key
        
        List<String> allDirs = (dirGroups*.key + outputDirs).unique()
        
        log.info "Creating directories on $hostname : ${dirGroups*.key}"
        ssh('sudo mkdir -p ' + allDirs.join(' ') + ' && sudo chmod uga+rwx ' + allDirs.join(' ') )
        
        dirGroups.each { dir, dirFiles ->
            log.info "Transfer $dirFiles to $hostname ..."
//            List sshCommand = ["scp","-oStrictHostKeyChecking=no", "-i", keypair,*dirFiles*.toString(), user + '@' +hostname+':'+dir]*.toString()
            List sshCommand = ["rsync", "-r", "-e", "ssh -oStrictHostKeyChecking=no -i $keypair", *dirFiles*.toString(), user + '@' +hostname+':'+dir]*.toString()
            Utils.executeCommand((List<Object>)sshCommand, throwOnError: true)        
        }
    }
    
    @Override
    @CompileStatic
    public void transferFrom(Map config, List<PipelineFile> fileList) {
        assert hostname != null && hostname != ""
        
        Map<String,List<PipelineFile>> dirGroups = fileList.groupBy { it.toPath().toAbsolutePath().parent.toString() }

        dirGroups.each { dir, dirFiles ->
            log.info "Transfer $dirFiles from $hostname ..."
//            String fileExpr = dirFiles.size()>1 ? "{${dirFiles*.name.join(',')}}" : dirFiles[0].name
            dirFiles.each { 
                def fileExpr = "$user@$hostname:$dir/$it.name"
                log.info("Transfer $fileExpr")
                List sshCommand = ["scp","-oStrictHostKeyChecking=no", "-i", keypair, fileExpr, dir]*.toString()
                Utils.executeCommand((List<Object>)sshCommand, throwOnError: true)        
            }
        }
    }

    @Override
    public void mountStorage(Map config) {
        def storageConfig = config.get('storage',null)
        if(storageConfig.is(null))
            return
//            throw new PipelineError("Please configure the storage attribute with a single value to use AWS")
            
        StorageLayer storage = StorageLayer.create(storageConfig)
        
        log.info "Created storage to run jobs in AWS: $storage"
        
        String mountCommand = storage.getMountCommand(this)
        
        log.info "Executing mount command for storage $storage: $mountCommand"
        
        ExecutedProcess p = this.ssh(mountCommand)
        
        log.info("Output from ssh mount command:  $p.out")
        log.info("Error output from ssh mount command: $p.err")

        // TODO: idea here is that certain things (eg: file transfer) need to behave differently depending
        // if storage is mounted or not
        this.storage = storage
    }
    
    @CompileStatic
    File getPipelineTmpDir() {
        new File("/tmp/bpipe-aws/$pipelineId/$command.id")
    }
}
