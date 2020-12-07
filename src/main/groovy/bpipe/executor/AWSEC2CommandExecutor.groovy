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

import bpipe.Command
import bpipe.CommandStatus
import bpipe.ExecutedProcess
import bpipe.ForwardHost
import bpipe.PipelineError
import bpipe.Utils
import bpipe.storage.StorageLayer

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Regions
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.DescribeInstancesResult
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.InstanceType
import com.amazonaws.services.ec2.model.Reservation
import com.amazonaws.services.ec2.model.RunInstancesRequest
import com.amazonaws.services.ec2.model.RunInstancesResult
import com.amazonaws.services.ec2.model.TerminateInstancesRequest
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder

import groovy.transform.CompileStatic
import groovy.transform.ToString

import java.util.logging.Logger


@ToString(includeNames=true)
@Mixin(ForwardHost)
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
     * Not used yet: the working directory to run commands in on the remote instance
     */
    String workingDirectory = 'work'
    
    protected transient AmazonEC2 ec2
    
    protected transient AmazonS3 s3client

    transient List<Process> logsProcesses
    
    boolean autoShutdown = true
    
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
            log.info "Checking exit code for ${commandId}"
            try {
                ExecutedProcess probe = ssh("if [ -e ${exitFile} ]; then cat ${exitFile}; else echo running; fi")
                String probeOutput = probe.out.toString().trim()
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
        // TODO Auto-generated method stub
        
    }
    
    @Override
    public void cleanup() {
        this.logsProcesses?.each { logsProcess ->
            if(logsProcess) {
                logsProcess.destroy()
                logsProcess = null
            }
        }
        
        if(autoShutdown) {
            log.info "Terminating instance $instanceId for executor $this"
            
            ec2.terminateInstances(new TerminateInstancesRequest([instanceId]))
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
        // TODO Auto-generated method stub
        
    }
    @Override
    public String statusMessage() {
        // TODO Auto-generated method stub
        return null;
    }
    @Override
    public List<String> getIgnorableOutputs() {
        // TODO Auto-generated method stub
        return null;
    }
    
    protected void createClient(Map config) {
        
        if(!config.containsKey('accessKey')) 
            throw new Exception('AWSEC2 executor requires the accessKey configuration setting')
            
        if(!config.containsKey('accessSecret')) 
            throw new Exception('AWSEC2 executor requires the accessSecret configuration setting')            
            
        if(!config.containsKey('keypair')) 
            throw new Exception('AWSEC2 executor requires the keypair configuration setting. Please set this to the path to the PEM file to allow SSH access to your instances')            
            
        if(!config.containsKey('user')) 
            throw new Exception('AWSEC2 executor requires the user configuration setting. Please set this to the id of the user that should be used for SSH commands in your image')            
            
        if(!config.containsKey('region')) 
            throw new Exception('AWSEC2 executor requires the region configuration setting. Please set this to the region in which you would like your instances to launch.')            
              
        this.keypair = config.keypair
        this.user = config.user
             
        AWSCredentials credentials = new BasicAWSCredentials(
            config.accessKey,
            config.accessSecret
        );
        
        ec2 = AmazonEC2ClientBuilder
            .standard()
            .withCredentials(new AWSStaticCredentialsProvider(credentials))
//            .withRegion(Regions.AP_SOUTHEAST_2)
            .withRegion(Regions.fromName(config.region))
            .build();        
        
          
        s3client = AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(Regions.AP_SOUTHEAST_2) // TODO: FIX
                .withRegion(Regions.fromName(config.region))
                .build();
        
    }
    
    @CompileStatic
    @Override
    public void acquireInstance(Map config, String image, String id) {
        
        this.autoShutdown = config.getOrDefault('autoShutdown', this.autoShutdown)
        
        createClient(config)
        
        InstanceType instanceType = InstanceType.T1Micro
        if(config.containsKey('instanceType')) {
            instanceType = InstanceType.fromValue((String)config.instanceType)
        }
        
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
        runInstancesRequest.withImageId(image)
//                .withInstanceType(InstanceType.T1Micro)
                .withInstanceType(instanceType)
                .withMinCount(1)
                .withMaxCount(1)
                .withKeyName(new File(keypair).name.replaceAll('.pem$',''))
                
        if(config.containsKey('securityGroup')) {
            runInstancesRequest = runInstancesRequest.withSecurityGroups((String)config.securityGroup)
        }         
                
        RunInstancesResult result = this.ec2.runInstances(runInstancesRequest)
        this.instanceId = result.reservation.instances[0].instanceId
        this.hostname = queryHostName()
        
        log.info "Instance $instanceId started with host name $hostname"
    }
    
    @CompileStatic
    protected String queryHostName() {
        String hostname = Utils.withRetries(8) {
            DescribeInstancesRequest request = new DescribeInstancesRequest();
            request.withInstanceIds(instanceId);
            DescribeInstancesResult result = ec2.describeInstances(request);
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
    

    @Override
    public void startCommand(Command command, Appendable outputLog, Appendable errorLog) {
        
        assert this.instanceId != null
        
        this.commandId = command.id
        
        File jobDir = this.getJobDir(commandId)
        
        String commandWorkDir = workingDirectory
        
        if(command.processedConfig?.containsKey('workingDirectory')) {
            commandWorkDir = command.processedConfig.workingDirectory
        }
        
        log.info "Working directory for command $command.id is $commandWorkDir"
        
        File cmdFile = new File(jobDir, "cmd.sh")
        cmdFile.text = """

            mkdir -p .bpipe/aws

            echo \$\$ | tee .bpipe/aws/$command.id

            HOMEDIR=`pwd`

            cd $commandWorkDir || { echo "Unable to change to expected working directory: $workingDirectory"; exit 1; }

            (

            $command.command

            ) > \$HOMEDIR/${remoteOutputPath} 2> \$HOMEDIR/${remoteErrorPath} 

            echo \$? > \$HOMEDIR/${exitFile}

        """.stripIndent()
        
        List sshCommand = ["ssh","-oStrictHostKeyChecking=no", "-i", keypair, this.user + '@' + this.hostname,"bash"]
        
        log.info "Lanching command saved in $cmdFile using: " + sshCommand.join(' ')
        
        ExecutedProcess proc = Utils.executeCommand([:], sshCommand) {
            redirectInput(cmdFile)
        }
        
        waitForProcessIdInOutput(proc)
       
        this.acquiring = false
        
        this.reconnect(outputLog, errorLog)
    }
    
    String getRemoteOutputPath() {
        ".bpipe/aws/${commandId}.out"
    }
    
    String getRemoteErrorPath() {
        ".bpipe/aws/${commandId}.err"
    }
    
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
    
    String getExitFile() {
       ".bpipe/aws/${commandId}.exit"
    }

    @Override
    public ExecutedProcess ssh(String cmd, Closure builder=null) {
        
        assert hostname != null && hostname != ""
        
        List<String> sshCommand = ["ssh","-oStrictHostKeyChecking=no", "-i", keypair, user + '@' +hostname,cmd]*.toString()
        
        return Utils.executeCommand(sshCommand, throwOnError: true)        
    }

    @Override
    public void mountStorage(Map config) {
        def storageConfig = config.get('storage',null)
        if(storageConfig.is(null))
            throw new PipelineError("Please configure the storage attribute with a single value to use AWS")
            
        StorageLayer storage = StorageLayer.create(storageConfig)
        
        log.info "Created storage to run jobs in AWS: $storage"
        
        String mountCommand = storage.getMountCommand(this)
        
        log.info "Executing mount command for storage $storage: $mountCommand"
        
        this.ssh(mountCommand)
    }

}
