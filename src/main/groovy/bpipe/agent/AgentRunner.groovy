package bpipe.agent

import bpipe.BpipeLogFormatter

import java.util.concurrent.Semaphore
import java.util.logging.FileHandler
import bpipe.Config
import groovy.util.logging.Log;

@Log
class AgentRunner {
    static void main(List<String> args) {
        
        CliBuilder cli = new CliBuilder(usage: "bpipe agent [-v] [-n <concurrency>")
        cli.with {
            v 'Verbose mode'
            n 'How many concurrent jobs to run', args:1, required:false
            s 'Wait for a single command and then exit (useful for debugging)', longOpt: 'single'
            t 'Set timeout to exit after if no commands received', longOpt: 'timeout', args: 1, type: Long
        }
        
        def opts = cli.parse(args)
        
        println "=" * Config.config.columns
        println "Starting Bpipe agent ..."
        println "=" * Config.config.columns
        
        initializeLogging()
        
        if(opts.v) 
            bpipe.Utils.configureVerboseLogging()
       
        Config.readUserConfig()
        
        List<Agent> agents = []
        
        List<ConfigObject> agentConfigs = []

        // Default agent, configured under "agent"
        if(Config.userConfig.containsKey('agent')) {
            Config.userConfig.agent.name = 'default'
            agentConfigs.add(Config.userConfig.agent)
        }
        
        for(Map.Entry e in Config.userConfig.agents) {
            log.info("Adding agent $e.key")
            e.value.name = e.key
            agentConfigs.add(e.value)
        }
        
        for(ConfigObject agentConfig : agentConfigs) {

            log.info("Configuring agent $agentConfig.name")

            bpipe.agent.Agent agent = new JMSAgent(agentConfig)
            
            agent.name = agentConfig.name
            
            def concurrency = agentConfig.getOrDefault('concurrency', opts.n)
            if(concurrency) {
                log.info("Setting concurrency = " + concurrency)
                agent.concurrency = new Semaphore(concurrency.toInteger())
            }
            agent.singleShot = agentConfig.getOrDefault('singleshot', opts.s)
            
            if(agentConfig.containsKey('bpipeHome')) {
                agent.bpipeHome = agentConfig.bpipeHome
                log.info("Using custom Bpipe home $agent.bpipeHome for agent $agent.name")
            }
                
            if(opts.t) {
                log.info "Scheduling timeout in $opts.t ms"
                new Thread({
                    Thread.sleep(opts.t)
                    if(agent.executed == 0) {
                        log.info "Timeout of $opts.t ms expired with no jobs executed: exiting"
                        agent.stopRequested = true
                    }
                    else  {
                        log.info "Timeout of $opts.t ms expired with ${agent.executed} jobs executed: not exiting"
                    }
                }).start()
            }
            
            agents.add(agent)
        }

        // If no other agent was specified, use the default HTTP agent
        if(!agents) {
            log.info("No agents configured: running default http agent")
            agents.add(new bpipe.agent.HttpAgent())
        }

        log.info "Launching ${agents.size()} agents"
        
        List<Thread> threads = agents.collect { agent ->
            new Thread(
                    {
                        agent.run()
                    }
                )
        }
        
        threads*.start()
        
        log.info("Started ${threads.size()} agents")
        
        threads*.join()
    }
    

    private static initializeLogging() {
        def parentLog = log.getParent()
        parentLog.getHandlers().each { parentLog.removeHandler(it) }

        File logFile = new File(System.getProperty("user.home")+"/.bpipedb/agent.log")
        if(logFile.exists()) {
            logFile.delete()
        }

        // The current log file
        FileHandler fh = new FileHandler(logFile.path)
        fh.setFormatter(new BpipeLogFormatter())
        parentLog.addHandler(fh)
    }
    
}
