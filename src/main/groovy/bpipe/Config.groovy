package bpipe

import groovy.util.ConfigObject;

import groovy.util.logging.Log;
import groovyx.gpars.GParsPool;

/**
 * Global configuration properties for Bpipe.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Log
class Config {
    
	/**
	 * Lower level configuration values.  These are not directly user 
	 * exposed.
	 */
    static Map<String,Object> config = [
        columns: 100,
        
        // Default mode is "run", but "define" will just produce a definition
        // of the pipeline without executing it.
        mode : "run",
        
        // By default all outputs get created in the current directory, but
        // the user can override it from the command line and in the future
        // some features might make outputs go to separate directories
        // (eg: per sample, etc.)
        defaultOutputDirectory : ".",

	    // Default name of doc pipeline html file
		defaultDocHtml: "index.html",

        // The maximum number of threads that Bpipe will launch 
        // when running jobs
        maxThreads : 32,
        
        // Whether the user set the threads or not by command line / config
        customThreads : false,
        
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
		eraseLogsOnExit : true, 
		
		// If set to true an HTML report of the run is generated
		report: false, 
		
		// The PID of the Bpipe Java process (set immediately after startup)
		pid: null,
		
		// The path to the file that is capturing the output for this 
		// Bpipe run 
		outputLogPath: null,
        
        // Stages where the pipeline should break at (set with -u on command line)
        breakAt: [],
        
        // Whether a break has been triggered
        breakTriggered : false,
		
		// Branches to run: if empty all branches are run, can be set on command line to limit
		// to a particular branches 
		branchFilter: []
    ]
        
    /**
     * Configured plugins
     */
    static Map<String,BpipePlugin> plugins = [:]
    
    /**
     * A list of pipeline stages that should not be included in diagrams
     */
    static Set<String> noDiagram = new HashSet()
    
    /**
     * Configuration loaded from the local directory
     */
    public static ConfigObject userConfig
    
    /**
     * Read user controlled configuration file from several cascading locations.
     * The different configuration files are read in order so that they take increasing
     * priority.
     * <li>.bpipeconfig in the user's home directory (lowest priority)
     * <li>bpipe.config in the location of the pipeline definition file
     * <li>bpipe.config in the location where the pipeline is running
     * 
     * (the last two above will often be the same, but when one pipeline for running many
     * different analyses it will be useful)
     */
    public static void readUserConfig() {
		
        ConfigSlurper slurper = new ConfigSlurper()
        
		File builtInConfigFile = new File(System.getProperty("bpipe.home") +"/bpipe.config")
		
		// Allows running in-situ in project source distro root dir to work
		if(!builtInConfigFile.exists()) {
			builtInConfigFile = new File(System.getProperty("bpipe.home") + "/src/main/config", "bpipe.config")
		}
        
        Map configFiles = [builtInConfig: builtInConfigFile]
        
        // The default way to prompt user for information is to ask at the console
        // Configuration in user home directory
		File homeConfigFile = new File(System.getProperty("user.home"), ".bpipeconfig")
		if(homeConfigFile.exists()) {
            configFiles.homeConfig = homeConfigFile
		}
        
        File configFile = new File("bpipe.config")
        
        // Configuration in directory next to main pipeline script
        if(config.script) {
            File pipelineConfigFile = new File(new File(config.script).absoluteFile.parentFile, "bpipe.config")
            if(pipelineConfigFile.exists() && (pipelineConfigFile.absolutePath != configFile.absolutePath)) {
                log.info "Reading Bpipe configuration from ${pipelineConfigFile.absolutePath}"
                configFiles.pipelineConfig = pipelineConfigFile
            }
            else {
                log.info "No configuration file found in same dir as pipeline file ${pipelineConfigFile.absolutePath}"
            }
        }
        
//        builtInConfig.prompts.handler = { msg ->
//            print msg
//            return System.in.withReader { it.readLine() }
//        }
        
        
        // Configuration in local directory (where pipeline is running)
        if(configFile.exists()) {
            log.info "Reading Bpipe configuration from ${configFile.absolutePath}"
            configFiles.localConfig = configFile
        }
        else {
            log.info "No local configuration file found"
        }
        
        Map<String,ConfigObject> configs
        GParsPool.withPool(configFiles.size()) {
            configs = configFiles.collectParallel { name, file ->
                Utils.time("Read config from $file") {
                    [name, slurper.parse(file.toURI().toURL())]
                }
            }.collectEntries()
        }
		
        userConfig = configs.builtInConfig ?: new ConfigObject()
		if(configs.homeConfig) {
			log.info "Merging home config file"
			userConfig.merge(configs.homeConfig)
		}
        
        if(configs.pipelineConfig) {
            log.info "Merging pipeline config file"
			userConfig.merge(configs.pipelineConfig)
        }
        
		if(configs.localConfig) {
			log.info "Merging local config file"
			userConfig.merge(configs.localConfig)
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
       
       if(userConfig.containsKey("noDiagram")) {
           def noDiagramStages = userConfig.noDiagram.split(",")
           log.info "The following stages are configured to be ignored in diagrams: $noDiagramStages"
           Config.noDiagram.addAll(noDiagramStages)
       }
    }
    
    static void initializePlugins() {
        // Check for plugins
        File pluginsDir = new File(System.properties["user.dir"]+"/.bpipe/plugins")
        if(!pluginsDir.exists()) {
            log.info "No plugins directory found: $pluginsDir"
            return
        }
        
        // For each subdirectory found, add and initialize the plugin
        pluginsDir.eachDir { File pluginDir ->
            Config.plugins[pluginDir.name] = new BpipePlugin(pluginDir.name, pluginDir)
        }
    }
}
