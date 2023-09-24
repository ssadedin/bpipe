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

import java.util.List
import java.util.Map
import java.util.concurrent.Semaphore
import bpipe.Command
import bpipe.CommandDependency
import bpipe.CommandStatus
import bpipe.ExecutedProcess
import bpipe.PipelineFile
import bpipe.Utils
import groovy.transform.CompileStatic
import groovy.util.logging.Log

/**
 * A cloud executor is an executor that requires additional steps to 
 * acquire the computing resource and map files between bpipe and the remote
 * system.
 * 
 * @author Simon Sadedin
 */
@Log
abstract class CloudExecutor implements PersistentExecutor {
    
    public static final long serialVersionUID = 0L
    
    /**
     * The command executed by the executor
     */
    String commandId 
    
    /**
     * The id of the instance in the cloud provider to which this executor is attached
     */
    String instanceId
    
    /**
     * The exit code for the command run by this executor, if any
     */
    Integer exitCode = null
    
    /**
     * The project within which this executor should work
     */
    String project
  
    /**
     * Flag to added to commands
     */
    String projectFlag
    
    /**
     * The command executed, but only if it was started already
     */
    transient Command command
    
    /**
     * Set to true after the command is observed to be finished
     */
    boolean finished = false
    
    /**
     * If files need to be transferred back from the cloud instance, this flag
     * is set to true after that has occurred
     */
    boolean transferredFrom = false
    
    /**
     * Whether the executor is currently in the process of acquiring its instance
     */
    boolean acquiring = false

    public void start(Map cfg, Command cmd, Appendable outputLog, Appendable errorLog) {
        
        // Acquire my instance
        acquiring = true
        
        this.command = cmd
        
        // Get the instance type
        String image = cfg.get('image')
        
        this.project = cfg.getOrDefault('project',null)
        
        if(this.instanceId == null) {
            if(cfg.containsKey('instanceId')) {
                this.instanceId = cfg.instanceId
                log.info "Connecting to existing cloud instance $instanceId for command $cmd.id"
                this.connectInstance(cfg)
            }
            else {
                log.info "Cloud executor is not connected to running instance via $cfg: acquiring instance"
                this.acquireInstance(cfg, image, cmd.id)
            }
        }
        else 
            this.acquiring = false
        
        this.command.save()
         
        // It can take a small amount of time before the instance can be ssh'd to - downstream 
        // functions will assume that an instance is available for SSH, so it's best to do
        // that check now
        this.waitForSSHAccess()
       
        this.mountStorage(cfg)
        
        // Provision dependencies
        for(CommandDependency dep : cmd.dependencies) {
            log.info("Executing dependency $dep for command $cmd.id")
            dep.provision(this)
        }
        
        this.transferFiles(cfg, cmd.inputs)
        
        // Execute the command via SSH
        this.startCommand(cmd, outputLog, errorLog)
    }
    
    @CompileStatic
    void transferFiles(Map config, List<PipelineFile> files) {
       
//        if(!((Map)config.getOrDefault('storage', null))?.getOrDefault('transfer', false)) {
//            return
//        }
        
        if(!config.getOrDefault('transfer', false))
            return
            
        this.transferTo(files)
    }
     
    
    @Override
//    @CompileStatic
    public int waitFor() {
        
        while(true) {
            CommandStatus status = this.status()
            if(status == CommandStatus.COMPLETE) { 
                
                this.stopForwarding()

                return exitCode
            }
                
            Thread.sleep(5000)
        }
    }

    abstract void acquireInstance(Map config, String image, String id)
    
    abstract void connectInstance(Map config)
    
    /**
     * Implementation specific method to execute a raw command over SSH
     * <p>
     * The implementation <em>must</em> throw an exception if the SSH command fails.
     */
    abstract ExecutedProcess ssh(Map options=[:], String cmd, Closure builder=null)

    abstract void transferTo(List<PipelineFile> files)

    abstract void transferFrom(Map config, List<PipelineFile> fileList) 

    abstract void startCommand(Command command, Appendable outputLog, Appendable errorLog) 
    
    abstract void mountStorage(Map config) 
    
    protected void waitForSSHAccess() {
        Utils.withRetries(8, backoffBaseTime:3000, message:"Test connect to $instanceId") {
            canSSH()
        }
    }
    
    
    /**
     * Scenario:
     * 
     *  - start pooled gce
     *  - it creates .bpipe in *shared* bucket
     *  - kill gce
     *    - .bpipe is left behind
     *  - start new pooled gce
     *    - it sees old .bpipe
     *  - now problem: it picks up wrong state!
     * 
     * Solution:
     * 
     *  - Q: what makes a pooled executor smart enough to use GC storage instead of local?
     *  - A: it's GCE itself that runs the pool command, so when it executes it naturally does
     *       that on the VM instance, in the work directory, resulting in the .bpipe files etc
     *  - Q: how then does the pooled executor write the command file to GC storage?
     * 
     * @param command
     * @return
     */
    File getJobDir(String commandId) {
        String jobDir = ".bpipe/commandtmp/$commandId"
		File jobDirFile = new File(jobDir)
        if(!jobDirFile.exists())
		    jobDirFile.mkdirs() 
        return jobDirFile
    }
    
    List getProjectArguments() {
        if(project)
            throw new bpipe.PipelineError("A project has been configured for cloud execution but executor type $this does not define how to map projects to command line instructions")
        return []
    }
    
    @Override
    void cleanup() {
        if(!command?.processedConfig)
            return
        if(!command.processedConfig.getOrDefault('transfer', false))
            return
         if(transferredFrom)
             return
             
        log.info("Command on $instanceId complete, transferring outputs $command.outputs back")
        if(exitCode == 0) {
            this.transferFrom(command.processedConfig, command.outputs)
            this.transferredFrom = true
        }
    }
    
    boolean canSSH() {
        ssh(timeout:5, 'true')
        
        return true // if we could not SSH, the command would have thrown
    }
    
    private static Semaphore cloudExecutorLaunchLock = new Semaphore(8)
   
    @Override
    public Semaphore getLaunchLock() {
        return cloudExecutorLaunchLock
    }
}
