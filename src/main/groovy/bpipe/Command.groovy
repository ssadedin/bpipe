/*
 * Copyright (c) 2013 MCRI, authors
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
package bpipe

import groovy.transform.CompileStatic
import groovy.util.logging.Log;

import java.nio.file.Path

import bpipe.executor.CommandExecutor
import bpipe.executor.ProbeCommandExecutor;;

@Log
class Command implements Serializable { 
    
    public static final long serialVersionUID = 0L
    
    /**
     * Bpipe id for the command - can be used by a {@link CommandExecutor} to
     * create unique files as it is guaranteed to be unique to this command
     */
    String id
    
    /**
     * The id of the stage that executed the command.
     * 
     * @see bpipe.PipelineStage#stageId
     */
    String stageId
    
    /**
     * Human readable short name for the command. Usually this is set to just
     * the stage name of the pipeline executing the command (so not guaranteed to
     * be unique!)
     */
    String name

    /**
     * This command's Branch
     */
    Branch branch

    /**
     * Actual command to be executed.
     */
    String command
    
    List<PipelineFile> outputs = []

    private List<PipelineFile> inputs 
    
    CommandExecutor executor
    
    String configName
    
    /**
     * When the command was initiated (eg: queued in a queuing system)
     */
    long createTimeMs
    
    /**
     * When the command started running
     */
    long startTimeMs = -1
    
    /**
     * When the command finished running (if we know)
     */
    long stopTimeMs
    
    /**
     * The last observed status for the command. This is 
     * set optionally by the executor
     */
    CommandStatus status
    
    /**
     * The exit code of the command IF it has exited
     */
    int exitCode = -1
    
    /**
     * Whether the command has had resources allocated
     */
    boolean allocated = false
    
    /**
     * Internal configuration = accessed via getConfig()
     */
    private Map cfg
    
    /**
     * Custom shell to use to run this command
     */
    List<String> shell 
    
    File dir
    
    transient List<CommandDependency> dependencies

    @CompileStatic
    Map getConfig(List<PipelineFile> inputs) {

        if(cfg != null)
            return cfg
            
        assert this.inputs == null
        
        this.inputs = inputs
        
        Map cfg = getConfig(this.configName, name, command, inputs)
        
        this.cfg = cfg
        
        return cfg
    }

    @CompileStatic
    static Map getConfig(String configName, String name, String command, List<PipelineFile> inputs) {

        // How to run the job?  look in user config
        if(!configName && (command != null)) // preallocated executors use a null command
            configName = command.trim().tokenize(' \t')[0].trim()
        
        log.info "Checking for configuration for command $configName"
        
        // Use default properties from root entries into user config
        def defaultConfig = Config.userConfig.findAll { !(it.value instanceof Map) }
        
        // allow nested config for default container
        if(Config.userConfig.containsKey('container')) {
            defaultConfig.container = Config.userConfig.container
        }
        
        // allow nested config for env
        if(Config.userConfig.containsKey('env')) {
            ConfigObject envCfg = (ConfigObject)Config.userConfig.env
            // Have to convert to plain Map because ConfigObject is not Serializeable
            defaultConfig.env = envCfg.collectEntries { [it.key, it.value] } 
        }
        
        log.info "Default command properties: " + Utils.sanitiseConfig(defaultConfig)
        
        Map rawCfg = defaultConfig
        
        def stageConfig = (ConfigObject)(Config.userConfig.containsKey("stages")?Config.userConfig.stages[name]:null)
        if(stageConfig && stageConfig instanceof Map)  {
            // override properties in default config with those for the specific stage
            rawCfg = defaultConfig + (Map<String,Object>)stageConfig
        }
 
        def cmdConfig = (ConfigObject)(Config.userConfig.containsKey("commands")?Config.userConfig.commands[configName]:null)
        if(cmdConfig && cmdConfig instanceof Map)  {
            // override properties in default config with those for the specific command
            rawCfg = rawCfg + (Map<String,Object>)cmdConfig
        }
        
        // Make a new map
        Map cfg = new HashMap(rawCfg)
        
        // Resolve inputs to files
        List<Path> fileInputs = (List<Path>)(inputs == null ? [] : inputs.collect { it.toPath() })
        
        // Execute any executable properties that are closures
        rawCfg.each { key, value ->
            if(value instanceof Closure) {
                
                Closure valueClosure = (Closure)value
                if(valueClosure.getMaximumNumberOfParameters() == 2) {
                    cfg[key] = valueClosure(fileInputs,cfg)
                }
                else {
                    cfg[key] = valueClosure(fileInputs)
                }
            }
            
            // Special case - walltime can be specified as integer number of seconds
            if(key == 'walltime' && !(cfg[key] instanceof String)) {
                cfg[key] = formatWalltime(cfg[key])
                log.info "Converted walltime is " + cfg[key]
            }
        }
        
        if(cfg.containsKey('container') && (cfg.container instanceof String || cfg.container instanceof GString)) {
            def container = Config.userConfig.containers[cfg.container.toString()]
            if(!container) 
                throw new PipelineError("""
                    Command specified container $cfg.container but this could not be resolved to any known configured container type.
                
                    Please configure an entry named $cfg.container in the containers section of your bpipe.config file
                """)

           cfg.container = Config.userConfig.containers[cfg.container.toString()]
        }
        
        // Ensure configuration knows its own name
        cfg.name = configName
        
        return cfg
    }
    
    /**
     * Return an already initialised configuration. Note that this means
     * getConfig() must have been called at least once with a list of inputs!
     * 
     * @return  processed configuration, converted to Map
     */
    @CompileStatic
    Map getProcessedConfig() {
        assert this.cfg != null
        return this.cfg
    }
    
    /**
     * @return the processed config which may be null if it was not initialised yet
     */
    @CompileStatic
    Map getRawProcessedConfig() {
        return this.cfg
    }
     
    Map setRawProcessedConfig(Map config) {
        this.cfg = config
    }
    
    private static String formatWalltime(def walltime) {
       // Treat as integer, convert to string
       walltime = walltime.toInteger()
       int hours = (int)Math.floor(walltime / 3600)
       int minutes = (int)Math.floor((walltime - hours*3600)/60)
       int seconds = walltime % 60
       return String.format('%02d:%02d:%02d', hours, minutes, seconds )
    }
    
    public void setStatus(String statusValue) {
        
        if(statusValue != this.status?.name())
            log.info "Command $id changing state from ${this.status} to $statusValue"
            
        try {
            CommandStatus statusEnum = CommandStatus.valueOf(statusValue)
            if(statusEnum != this.status) {
                
                if((statusEnum == CommandStatus.RUNNING) ||
                   ((this.status == null || status == CommandStatus.WAITING || this.status == CommandStatus.QUEUEING) && (statusEnum==CommandStatus.COMPLETE || statusEnum==CommandStatus.EXITING))) {
                    this.startTimeMs = System.currentTimeMillis()
                }
                else
                // If we did not set start time but the command is transitioning to an exit state, set it now
                // I think this happens when the command executes so quickly that there is no poll of any state before it runs and finishes
                if((this.status == CommandStatus.UNKNOWN && this.startTimeMs < 0) && (statusEnum==CommandStatus.COMPLETE || statusEnum==CommandStatus.EXITING)) {
                    this.startTimeMs = System.currentTimeMillis()
                }
            }
            this.status = statusEnum
                
        }
        catch(Exception e) {
            log.warning("Failed to update status for result $statusValue: $e")
        }
    }
    
    synchronized void save() {
        
       def e = this.executor
       if(e instanceof bpipe.executor.ThrottledDelegatingCommandExecutor)
            e = e.commandExecutor
  
       if(!dir.exists())
           dir.mkdirs()
           
       // Temporarily swap out the config if necessary
       def tempCfg = Utils.configToMap(cfg)
          
       File saveFile = new File(dir, this.id)
       Command me = this
       
       saveFile.withObjectOutputStream { ois ->
           ois.writeObject(e)
           ois.writeObject(me)
       } 
       
       cfg = tempCfg
    }
    
    static Command load(File f) {
        Command cmd
        f.withObjectInputStream {
            CommandExecutor exec = it.readObject()
            cmd = it.readObject()
        }
        return cmd
    }

    boolean isResourcesSatisfied() {
        return this.allocated || (this.executor != null && this.executor instanceof ProbeCommandExecutor)
    }
    
   transient Closure commandListener
    
    void setCommand(String cmd) {
        this.command = cmd
        if(commandListener != null)
            commandListener(this)
    }
    
    List<PipelineFile> getInputs() {
        return this.@inputs
    }
    
    static Command readCommand(File saveFile) {
        saveFile.withObjectInputStream { ois ->
            CommandExecutor exe2 = ois.readObject()
            Command c2 = ois.readObject()
            return c2
        }       
    }
    
    String toString() {
       "Command $id for stage $name ($stageId) in branch $branch (status=$status)"
    }
}
