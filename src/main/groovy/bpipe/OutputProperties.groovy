package bpipe

import groovy.transform.CompileStatic;
import groovy.util.logging.Log;

@Log
@CompileStatic
class OutputProperties implements Serializable {
    
    public static final long serialVersionUID = 0L
    
    File outputFile
    
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
    
    String getOutputPath() {
        return outputFile.path
    }
    
    String propertiesFile = null
    
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
        result.inputs = (List)p.inputs
        result.cleaned = (boolean)p.cleaned
        result.outputFile = (File)p.outputFile
        result.timestamp = (long)p.timestamp
        result.canonicalPath = (String)p.canonicalPath
        result.preserve = (boolean)p.preserve
        result.intermediate = (boolean)p.intermediate
        result.stopTimeMs = (long)p.stopTimeMs
        result.createTimeMs = (long)p.createTimeMs
        result.propertiesFile = f.name
        return result
    }
    
    Properties getProperties() {
        readProperties(new File(".bpipe/outputs/$propertiesFile"))
    }
    
    /**
     * Read the given file as an output meta data file, parsing
     * various expected properties to native form from string values.
     */
    @CompileStatic
    static Properties readProperties(File f) {
        log.info "Reading property file $f"
        def p = new Properties();
        new FileInputStream(f).withStream { p.load(it) }
        p.inputs = p.inputs? ((String)(p.inputs)).split(",") as List : []
        p.cleaned = p.containsKey('cleaned')?Boolean.parseBoolean((String)p.cleaned) : false
        if(!p.outputFile)  {
            log.warning("Error: output meta data property file $f is missing essential outputFile property")
            System.err.println ("Error: output meta data property file $f is missing essential outputFile property")
            System.err.println ("Properties are: " + p)
            return null
        }
        
        File outputFile = new File((String)p.outputFile)
            
        p.outputFile = outputFile;
        
        // Normalizing the slashes in the path is necessary for Cygwin compatibility
//        p.outputPath = outputFile.path.replaceAll("\\\\","/")
        
        // If the file exists then we should get the timestamp from there
        // Otherwise just use the timestamp recorded
        if(outputFile.exists())
            p.timestamp = outputFile.lastModified()
        else
            p.timestamp = Long.parseLong((String)p.timestamp)

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
            
        p.preserve = Boolean.parseBoolean((String)p.preserve)
        p.intermediate = Boolean.parseBoolean((String)p.intermediate)
        p.commandId = (p.commandId!=null)?p.commandId:"-1"
        p.startTimeMs = p.startTimeMs?Long.parseLong((String)p.startTimeMs):0
        p.createTimeMs = p.createTimeMs?Long.parseLong((String)p.createTimeMs):0
        p.stopTimeMs = p.stopTimeMs?Long.parseLong((String)p.stopTimeMs):0
        
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
