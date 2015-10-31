package bpipe

import groovy.util.logging.Log;

@Log
class OutputProperties implements Serializable {
    
    public static final long serialVersionUID = 0L
    
    File outputFile
    
    String outputPath
    
    boolean cleaned
    
    boolean preserve
    
    boolean intermediate
    
    List<String> inputs
    
    long lastModified
    
    long timestamp
    
    long stopTimeMs
    
    long createTimeMs
    
    transient long maxTimestamp
    
    transient boolean upToDate
    
    String canonicalPath
    
    /**
     * Read the given file as an output meta data file, parsing
     * various expected properties to native form from string values.
     */
    static OutputProperties read(File f) {
        log.info "Reading property file $f"
        def p = readProperties(f)
        if(p == null)
            return null
        
        OutputProperties result = new OutputProperties()
        result.inputs = p.inputs
        result.cleaned = p.cleaned
        result.outputFile = p.outputFile
        result.outputPath = p.outputPath
        result.timestamp = p.timestamp
        result.canonicalPath = p.canonicalPath
        result.preserve = p.preserve
        result.intermediate = p.intermediate
        result.stopTimeMs = p.stopTimeMs
        result.createTimeMs = p.createTimeMs
        return result
    }
    
    /**
     * Read the given file as an output meta data file, parsing
     * various expected properties to native form from string values.
     */
    static Properties readProperties(File f) {
        log.info "Reading property file $f"
        def p = new Properties();
        new FileInputStream(f).withStream { p.load(it) }
        p.inputs = p.inputs?p.inputs.split(",") as List : []
        p.cleaned = p.containsKey('cleaned')?Boolean.parseBoolean(p.cleaned) : false
        if(!p.outputFile)  {
            log.warning("Error: output meta data property file $f is missing essential outputFile property")
            System.err.println ("Error: output meta data property file $f is missing essential outputFile property")
            System.err.println ("Properties are: " + p)
            return null
        }
            
        p.outputFile = new File(p.outputFile)
        
        // Normalizing the slashes in the path is necessary for Cygwin compatibility
        p.outputPath = p.outputFile.path.replaceAll("\\\\","/")
        
        // If the file exists then we should get the timestamp from there
        // Otherwise just use the timestamp recorded
        if(p.outputFile.exists())
            p.timestamp = p.outputFile.lastModified()
        else
            p.timestamp = Long.parseLong(p.timestamp)

        // The properties file may have a cached version of the "canonical path" to the
        // output file. However this is an absolute path, so we can only use it if the
        // base directory is still the same as when this property file was saved.
        if(!p.containsKey("basePath") || (p["basePath"] != Runner.runDirectory)) {
            p.remove("canonicalPath")
        }
        
        if(!p.containsKey('preserve'))
            p.preserve = 'false'
            
        if(!p.containsKey('intermediate'))
            p.intermediate = 'false'
            
        p.preserve = Boolean.parseBoolean(p.preserve)
        p.intermediate = Boolean.parseBoolean(p.intermediate)
        p.commandId = (p.commandId!=null)?p.commandId:"-1"
        p.startTimeMs = (p.startTimeMs?:0).toLong()
        p.createTimeMs = (p.createTimeMs?:0).toLong()
        p.stopTimeMs = (p.stopTimeMs?:0).toLong()
        
        if(!p.containsKey("tools")) {
            p.tools = ""
        }
        
        if(!p.containsKey("branchPath")) {
            p.branchPath = ""
        }
        
        if(!p.containsKey("stageName")) {
            p.stageName = ""
        }
        return p
    }
}
