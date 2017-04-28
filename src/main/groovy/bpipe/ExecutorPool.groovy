package bpipe

import java.util.List
import java.util.Map

import bpipe.executor.CommandExecutor
import groovy.transform.CompileStatic
import groovy.util.logging.Log

@Log
class PooledExecutor implements CommandExecutor {
    
    public static final long serialVersionUID = 0L
    
    /**
     * The executor that will run the command
     */
    @Delegate
    CommandExecutor executor
    
    /**
     * The dummy command object used to initialize the execution; not the real command!
     */
    Command command
    
    Map poolConfig
    
    /**
     * An output log that will forward to the real output log, when
     * the command activate
     */
    transient ForwardingOutputLog outputLog
    
    transient String currentCommandId = null
    
    transient Closure onFinish =  null
    
    /**
     * File that acts as a flag to tell this executor to stop
     */
    File stopFile
    
    File heartBeatFile
    
    String hostCommandId
    
    void setHostCommandId(String value) {
        this.hostCommandId = value
    }

    /**
     * Triggers the waiting pooled executor to start running a command.
     * <p>
     * Note: the pooled executor does not need to actually start the command: the command is already
     * waiting to start, and will start as soon as the command script appears.
     */
    @Override
    public void start(Map cfg, Command cmd, Appendable outputLog, Appendable errorLog) {
        
        File cmdScript = new File(".bpipe/commandtmp/$hostCommandId", ExecutorPool.POOLED_COMMAND_FILENAME + ".${currentCommandId}.sh")
        File cmdScriptTmp = new File(".bpipe/commandtmp/$hostCommandId", ExecutorPool.POOLED_COMMAND_FILENAME + ".tmp")
        cmdScriptTmp.text = cmd.command
        cmdScriptTmp.renameTo(cmdScript)
    }
    
    @Override
    int waitFor() {
        
        File exitCodeFile = new File(".bpipe/commandtmp/$hostCommandId/${currentCommandId}.pool.exit")
        
        // wait until result status file appears
        while(!exitCodeFile.exists()) {
            Thread.sleep(1000)
        }
        
        Thread.sleep(100)
        
        int exitCode = exitCodeFile.text.trim().toInteger()
        
        if(onFinish != null)
            onFinish()
        
        return exitCode
    }
    
    /**
     * Return true if this pooled executor is able to run the given job,
     * based on its configuration.
     * 
     * @param cfg
     * @return
     */
    boolean canAccept(Map cfg) {
        
        // Check: is the amount of time left in my own job budget sufficient
        // to run the given command?
        String myWalltimeConfig = this.poolConfig.get("walltime",null)
        
        // How long does the job need?
        if(cfg.walltime && myWalltimeConfig) {
            
            long myWallTimeMs = Utils.walltimeToMs(myWalltimeConfig)
            long timeRemainingMs = myWallTimeMs - (System.currentTimeMillis() - this.command.createTimeMs)
            long walltimeMs = Utils.walltimeToMs(cfg.walltime)
            
            log.info "Pooled job $hostCommandId checking time remaining ($timeRemainingMs ms) compared to requirements ($walltimeMs)."
            if(walltimeMs > timeRemainingMs) {
                log.info "Pooled job $hostCommandId has insufficient time remaining ($timeRemainingMs ms) compared to requirements ($walltimeMs). Rejecting job request."
                return false;
            }
        }
        
        // TODO: check other attributes such as memory and procs
        return true
    }
    
    void execute(Command command, OutputLog log) {
        this.currentCommandId = command.id
        this.command.adopt(command) 
        this.outputLog.wrapped = log
        this.command.executor = this
        
        // Note: this will trigger an event to the waiting command executor that
        // will make it write out the file and execute the command
        this.command.command = command.command
    }
    
    @Override
    void stop() {
        
        this.executor.stop()
            
        // Write out the stop flag
        log.info "Writing stop file: $stopFile"
        this.stopFile.text = String.valueOf(System.currentTimeMillis())
        this.heartBeatFile.delete()
    }
}

@Log
class ExecutorPool {
    
    public static final String POOLED_COMMAND_FILENAME = "pool_cmd"
    
    public static final String POOLED_COMMAND_STOP_FILENAME = "stop"
    
    Map cfg
    
    List<PooledExecutor> executors = []
    
    ExecutorFactory executorFactory
    
    long startTimeMs 
    
    int poolSize
    
    Map jobCfg
    
    List<String> configs
    
    ExecutorPool(ExecutorFactory executorFactory, Map poolCfg) {
        
        this.executorFactory = executorFactory
        this.cfg = poolCfg
        this.jobCfg = jobCfg
        
        def configs = poolCfg.get('configs') ?: [ e.key ]
        if(configs instanceof String) {
            configs = configs.tokenize(',')*.trim()
        }
        this.configs = configs

        this.poolSize = String.valueOf(poolCfg.get('jobs') ?: 1).toInteger()

        log.info "Found pre-allocation pool $poolCfg.name for ${configs.join(',')} of size $poolSize"
    }
    
    static final long HEARTBEAT_INTERVAL_SECONDS = 10
    
    /**
     * Launches a wrapper job that waits for child jobs to be assigned and executes them.
     * <p>
     * This job loops "forever" waiting for child jobs, which are assigned by writing a file
     * in the file "pool_cmd.<job_id>.sh". When such a file is observed, the wrapper job 
     * parses out the job id and executes the job.
     * <p>
     * One key aspect is how the wrapper job knows when to exit. It occurs via multiple
     * mechanisms:
     * <li> Bpipe writes a "stop" file as a flag that it should exit; this is checked
     *      once every second and the job exits as soon as this flag is observed.
     * <li> In case Bpipe is killed, a "heartbeat" file is checked every 15 seconds.
     *      at each 15 second interval, the file is removed. If the file is not recreated,
     *      the wrapper script exits. This ensures that it Bpipe exits, so too does the
     *      wrapper script, so we do not "leak" jobs.
     * <li> The job can be stopped by the regular "stop" mechanism that Bpipe uses 
     *      as well
     */
    synchronized void start() {
        
        startTimeMs = System.currentTimeMillis()
        
        log.info "Allocating ${poolSize} executors for pool ${cfg.name}"
        for(int i=0; i<poolSize; ++i) {
            
            Command cmd = new Command(
                name:cfg.name, 
                configName: cfg.name, 
                id: CommandId.newId(),
                dir: new File(CommandManager.DEFAULT_COMMAND_DIR)
            )
            
            Map execCfg = cmd.getConfig(null)
            
            CommandExecutor cmdExec = executorFactory.createExecutor(execCfg)
            cmd.executor = cmdExec
            
            String pooledCommandScript = ".bpipe/commandtmp/$cmd.id/$POOLED_COMMAND_FILENAME"
            String poolCommandStopFlag = ".bpipe/commandtmp/$cmd.id/$POOLED_COMMAND_STOP_FILENAME"
            File heartBeatFile = new File(".bpipe/commandtmp/$cmd.id/heartbeat")
            
            File stopFile = new File(".bpipe/commandtmp/${cmd.id}/$POOLED_COMMAND_STOP_FILENAME")
            
            def debugLog = ('debugPooledExecutor' in cfg) ? "true" : "false"
            
            cmd.command = """

                export POOL_ID="$cfg.name"

                i=0
                while true;
                do
                    while [ ! -e ${pooledCommandScript}.[0-9]*.sh ];
                    do

                        if [ -e "$stopFile" ];
                        then
                            $debugLog && { echo "Pool command exit flag detected: $stopFile" >> pool.log; }
                            exit 0
                        fi

                        sleep 1

                        $debugLog && { echo "No: ${pooledCommandScript}.[0-9]*.sh" >> pool.log; }

                        let 'i=i+1'
                        $debugLog && { echo "i=\$i" >> pool.log; }
                        if [ "\$i" == ${HEARTBEAT_INTERVAL_SECONDS+5} ];
                        then
                            if [ ! -e "$heartBeatFile" ];
                            then
                                $debugLog && { echo "Heartbeat not found: exiting" >> pool.log; }
                                exit 0
                            fi
                            $debugLog && { echo "Remove heartbeat: $heartBeatFile" >> pool.log; }
                            i=0
                            rm $heartBeatFile
                        else
                            $debugLog && { echo "In between heartbeat checks: \$i" >> pool.log; }
                        fi
                    done

                    POOL_COMMAND_SCRIPT=`ls ${pooledCommandScript}.[0-9]*.sh` 
                    POOL_COMMAND_SCRIPT_BASE=\${POOL_COMMAND_SCRIPT%%.sh}
                    POOL_COMMAND_ID=\${POOL_COMMAND_SCRIPT_BASE##*.}

                    $debugLog && { echo "Pool $cmd.id Executing command: \$POOL_COMMAND_ID" >> pool.log; }

                    mv \$POOL_COMMAND_SCRIPT $pooledCommandScript

                    POOL_COMMAND_EXIT_FILE=.bpipe/commandtmp/$cmd.id/\${POOL_COMMAND_ID}.pool.exit

                    bash -e $pooledCommandScript
    
                    echo \$? > \$POOL_COMMAND_EXIT_FILE
                done
            """
            
            OutputLog outputLog = new ForwardingOutputLog()
            
            // Sometimes this can be the first code that runs and needs this
            new File(".bpipe/commands").mkdirs()
            
            cmdExec.start(execCfg, cmd, outputLog, outputLog)
            
            cmd.createTimeMs = System.currentTimeMillis()
            
            log.info "Started command $cmd.id as instance $i of pooled command of type $cfg.name"
            
            Poller.instance.timer.schedule({
                if(!heartBeatFile.exists()) {
                    heartBeatFile.text = String.valueOf(System.currentTimeMillis())
                }
            }, 0, HEARTBEAT_INTERVAL_SECONDS*1000)
            
            PooledExecutor pe = new PooledExecutor(executor:cmdExec, command: cmd)
            pe.outputLog = outputLog
            pe.setHostCommandId(cmd.id)
            pe.stopFile = stopFile
            pe.heartBeatFile = heartBeatFile
            pe.poolConfig = this.cfg.collectEntries { it }
            pe.onFinish = {
                log.info "Adding pooled executor $cmd.id back into pool"
                executors << pe
            }
            this.executors << pe
        }
    }
    
    synchronized Command take(Command command, OutputLog outputLog) {
        
        if(executors.isEmpty())
            return command
        
        PooledExecutor pe = null;
        
        for(PooledExecutor candidatePe in executors) {
            if(candidatePe.canAccept(command.processedConfig)) {
                pe = candidatePe
            }
        }
        
        if(!pe) {
            log.info "No compatible pooled executor available from ${executors.size()} pooled commands for command $command.id (config=$command.configName)"
            return
        }
        
        executors.remove(pe)
        
        pe.execute(command, outputLog)
        
        return pe.command
    }
    
    @CompileStatic
    void shutdown() {
        log.info "Shutting down ${executors.size()} jobs in preallocation pool ${cfg.name}"
        for(PooledExecutor pe in executors) {
            pe.stop()
        }
    }
    
    /**
     * Preallocation pools, indexed by the name of the pool
     */
    static Map<String,ExecutorPool> pools = [:]
    
    /**
     * Read the given userConfig and parse the "preallocate" section to find configurations
     * which should have preallocation pools created.
     * 
     * @param executorFactory
     * @param userConfig
     */
    @CompileStatic
    synchronized static void initPools(ExecutorFactory executorFactory, ConfigObject userConfig) {
        // Each prealloc section looks like
        //
        // small { // name of pool, by default the name of the configs it applies to
        //     jobs=10
        //     configs="bwa,gatk" // optional: now it will be limited to these configs
        // }
        
        // Create the pools
        ConfigObject prealloc = (ConfigObject) userConfig.get("preallocate")
        for(Map.Entry e in prealloc) {
            ConfigObject cfg = e.value
            
            if(!('configs' in cfg))
                cfg.configs = e.key
           
            if(!('name' in cfg))
                cfg.name = e.key
                
            pools[(String)cfg.name] = new ExecutorPool(executorFactory, cfg)
        }
        
        // Start all the pools
        pools*.value*.start()
    }
    
    /**
     * Shut down all the preallocation pools. If any jobs are still waiting pending commands
     * then they will be terminated.
     */
    synchronized static void shutdownAll() {
        log.info "Shutting down ${pools.size()} preallocation pools"
        for(ExecutorPool pool in pools*.value) {
            try {
                pool.shutdown()
            }
            catch(Exception e) {
                log.warning("Failed to shutdown preallocation pool: " + pool.cfg.name)
            }
        }
    }
    
    /**
     * If a pooled executor that is compatible with the given command is available,
     * returns a new command with the executor assigned. Otherwise returns the
     * original command without changing the executor.
     * 
     * @param command
     * @param cfg
     * @param outputLog
     * 
     * @return  A Command object that is either the input command unchanged, or a new command
     *          with an executor assigned.
     */
    synchronized static Command requestExecutor(Command command, Map cfg, OutputLog outputLog) {
        pools.each { name, pool -> 
            pool.configs.contains(cfg.name) 
        }?.value
        
        
        for(Map.Entry poolEntry in pools) {
            ExecutorPool pool = poolEntry.value
            if(!pool.configs.contains(cfg.name))
                continue
                
            Command newCommand = pool.take(command, outputLog)
            if(newCommand)
                return newCommand
        }
        
        return command
    }
}
