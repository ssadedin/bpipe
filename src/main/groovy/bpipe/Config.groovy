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
        maxThreads : 32,
        
        // Whether to enable detection of changes to commands so
        // that outputs produced by them can be invalidated
        // This feature is still experimental, so off by 
        // default for now
        enableCommandTracking : false, 
		
		// For many commands we don't want the log files to persist
		// eg: the output of the help info
		// This flag is read on shutdown and if true, a 
		// marker file is written that causes the log files to be 
		// ignored / cleaned up 
		eraseLogsOnExit : true
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
            
            
       // Allow user to over ride any value in config with local config 
       userConfig.flatten().each { k,v ->
           
           if(k == "mode") 
               return
           
           if(config.containsKey(k)) {
               log.info "Overriding default config value ${config[k]} with user defined value ${v}"
               config[k] = v
           }
       }
    }
}