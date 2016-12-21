package bpipe

/**
 * A wrapper for either a RegionSet or a Chr that
 * enables them to be used as either string values or to support a ".bed"
 * extension.
 */
class RegionValue implements Serializable {
    
    public static File REGIONS_DIR = new File(".bpipe/regions")
    
    public static final long serialVersionUID = 0L
    
    String value
    
    String regions
    
    String getRegions() {
        if(regions == null) {
            if(value.endsWith(".bed")) {
                File bedFile = new File(value)
                if(!bedFile.exists()) 
                    throw new FileNotFoundException(value, "The file $value was specified as the region for the pipeline but does not exist or could not be accessed")
                    
                regions = bedFile.readLines().collect { line -> def fields = line.tokenize('\t'); fields[0] + ":" + fields[1] + "-" + fields[2] }.join(" ")
            }
            else
                regions = value
        }
        return regions
    }
    
    def propertyMissing(String name) {
        
       if(name == "bed") {
           
           if(!REGIONS_DIR.exists()) {
               REGIONS_DIR.mkdirs()
           }
           
           def fn = new File(REGIONS_DIR,Utils.sha1(getRegions())+".bed")
           if(!fn.exists()) {
               fn.text = getRegions().replaceAll("-","\t").replaceAll(":","\t").split(" ").join("\n") + "\n"
           }
           return fn.absolutePath
       } 
    }
    
    String plus(String arg) {
        return this.toString() + arg
    }
    
    def methodMissing(String name, args) {
        // faux inheritance from String class
        if(name in String.metaClass.methods*.name)
            return String.metaClass.invokeMethod(this.toString(), name, args)
        else {
            throw new MissingMethodException(name, RegionValue, args)
        }
    }
    
    String toString() {
        return getRegions()
    }
}
