package bpipe.agent

import bpipe.BpipeLogFormatter
import java.util.logging.FileHandler
import bpipe.Config
import groovy.util.logging.Log;

@Log
class AgentRunner {
    static void main(String [] args) {
        
        CliBuilder cli = new CliBuilder(usage: "bpipe agent [-v]")
        cli.with {
            v 'Verbose mode'
        }
        
        def opts = cli.parse(args)
        
        println "=" * Config.config.columns
        println "Starting Bpipe agent ..."
        println "=" * Config.config.columns
        
        initializeLogging()
        
        if(opts.v) 
            bpipe.Utils.configureVerboseLogging()
       
        Config.readUserConfig()
        Timer t = new Timer()
        bpipe.agent.Agent agent = new bpipe.agent.Agent()
        t.schedule(agent, 1000, 3000)
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
