package bpipe

import groovy.util.ConfigObject;

import java.util.logging.Logger;

/**
 * Global configuration properties for Bpipe.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
class Config {
    
    private static Logger log = Logger.getLogger("bpipe.Config");
    
    static config = [
        columns: 100,
        
        // Default mode is "run", but "define" will just produce a definition
        // of the pipeline without executing it.
        mode : "run",
        
        // By default all outputs get created in the current directory, but
        // the user can override it from the command line and in the future
        // some features might make outputs go to separate directories
        // (eg: per sample, etc.)
        defaultOutputDirectory : ".",
        
        // The maximum number of threads that Bpipe will launch 
        // when running jobs
        maxThreads : 32
    ]
    
    /**
     * Configuration loaded from the local directory
     */
    public static ConfigObject userConfig
    
    public static void readUserConfig() {
        File configFile = new File("bpipe.config")
        if(configFile.exists()) {
            log.info "Reading Bpipe configuration from ${configFile.absolutePath}"
            ConfigSlurper slurper = new ConfigSlurper()
            userConfig = slurper.parse(configFile.toURI().toURL())
        }
        else {
            log.info "No local configuration file found"
            userConfig = new ConfigObject()
        }
            
        if(!userConfig.executor) {
            userConfig.executor = "local"
        }
        else
            log.info "Default executor is $userConfig.executor"
    }
}