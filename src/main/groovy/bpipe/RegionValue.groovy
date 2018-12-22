package bpipe

import groovy.transform.CompileStatic

/**
 * A wrapper for either a RegionSet or a Chr that
 * enables them to be used as either string values or to support a ".bed"
 * extension.
 */
@CompileStatic
class RegionValue implements Serializable {
    
    public static File REGIONS_DIR = new File(".bpipe/regions")
    
    public static final long serialVersionUID = 0L
    
    String id
    
    String value
    
    String regions
    
    RegionValue(String value) {
        this.value = value
        initId()
    }
    
    RegionValue(Iterable<Sequence> sequences) {
        this.value = sequences.collect { Sequence s -> "$s.name:$s.range.from-$s.range.to" }.join(" ")
        initId()
    }

    private initId() {
        if(this.value.isEmpty())
            this.id = "empty"
        else
            this.id = Utils.sha1(this.value).substring(0,8)
    }
    
    @CompileStatic
    String getRegions() {
        if(regions == null) {
            if(value.endsWith(".bed")) {
                File bedFile = new File(value)
                if(!bedFile.exists()) 
                    throw new FileNotFoundException("The file $value was specified as the region for the pipeline but does not exist or could not be accessed")
                    
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
           File fn = getBedFile()
           return fn.absolutePath
       } 
    }
    
    String bedFlag(String flag) {
        if(this.isEmpty()) {
            return ""
        }
       File fn = getBedFile()
       return "$flag $fn.absolutePath"
    }

    private File getBedFile() {
        
        if(!REGIONS_DIR.exists())
            REGIONS_DIR.mkdirs()
            
        def fn = new File(REGIONS_DIR,Utils.sha1(getRegions())+".bed")
        if(!fn.exists()) {
            fn.text = getRegions().replaceAll("-","\t").replaceAll(":","\t").split(" ").join("\n") + "\n"
        }
        return fn
    }
    
    String plus(String arg) {
        return this.toString() + arg
    }
    
    boolean isEmpty() {
        this.value.size() == 0
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
