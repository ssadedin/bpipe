package bpipe

import java.util.List
import java.util.Map

import bpipe.executor.CommandExecutor
import groovy.transform.CompileStatic
import groovy.util.logging.Log

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
    
    /**
     * An output log that will forward to the real output log, when
     * the command activate
     */
    transient ForwardingOutputLog outputLog

    /**
     * Triggers the waiting pooled executor to start running a command.
     * <p>
     * Note: the pooled executor does not need to actually start the command: the command is already
     * waiting to start, and will start as soon as the command script appears.
     */
    @Override
    public void start(Map cfg, Command cmd, Appendable outputLog, Appendable errorLog) {
        File cmdScript = new File(".bpipe/commandtmp/$cmd.id", ExecutorPool.POOLED_COMMAND_FILENAME)
        File cmdScriptTmp = new File(".bpipe/commandtmp/$cmd.id", ExecutorPool.POOLED_COMMAND_FILENAME + ".tmp")
        cmdScriptTmp.text = cmd.command
        cmdScriptTmp.renameTo(cmdScript)
    }
}

@Log
class ExecutorPool {
    
    public static final String POOLED_COMMAND_FILENAME = "pool_cmd.sh"
    
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
            
            cmd.command = """

                export POOL_ID="$cfg.name"

                while [ ! -e $pooledCommandScript ];
                do
                    sleep 1
                done

                bash -e $pooledCommandScript
            """
            
            OutputLog outputLog = new ForwardingOutputLog()
            
            cmdExec.start(execCfg, cmd, outputLog, outputLog)
            
            log.info "Started command $cmd.id as instance $i of pooled command of type $cfg.name"
            
            PooledExecutor pe = new PooledExecutor(executor:cmdExec, command: cmd, outputLog:outputLog)
            this.executors << pe
        }
    }
    
    synchronized Command take(Command command, OutputLog outputLog) {
        
        if(executors.isEmpty())
            return command
        
        PooledExecutor pe = executors.pop()
        pe.command.adopt(command) 
        pe.outputLog.wrapped = outputLog
        pe.command.executor = pe
        
        // Note: this will trigger an event to the waiting command executor that
        // will make it write out the file and execute the command
        pe.command.command = command.command
        return pe.command
    }
    
    @CompileStatic
    void shutdown() {
        log.info "Shutting down ${executors.size()} jobs in preallocation pool ${cfg.name}"
        for(PooledExecutor pe in executors) {
            pe.executor.stop()
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
        
        ConfigObject prealloc = (ConfigObject) userConfig.get("preallocate")
        for(Map.Entry e in prealloc) {
            ConfigObject cfg = e.value
            
            if(!('configs' in cfg))
                cfg.configs = e.key
           
            if(!('name' in cfg))
                cfg.name = e.key
                
            pools[(String)cfg.name] = new ExecutorPool(executorFactory, cfg)
            pools*.value*.start()
        }
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
     * original command with the executor set to null.
     * 
     * @param command
     * @param cfg
     * @param outputLog
     * @return  A Command object that is either the input command unchanged, or a new command
     *          with an executor assigned.
     */
    synchronized static Command requestExecutor(Command command, Map cfg, OutputLog outputLog) {
        ExecutorPool pool = pools.find { it.value.configs.contains(cfg.name) }?.value
        if(pool == null)
            return command
        
        Command result = pool.take(command, outputLog)
        if(pool.executors.isEmpty())
            pool.executors.remove(cfg.name)
            
        return result
    }
    
}
