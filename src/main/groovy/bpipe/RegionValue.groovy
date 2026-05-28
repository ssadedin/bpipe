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
    
    int rangeFrom = 0
    
    int rangeTo = 0
    
    int totalSize = 0
    
    Set<String> chromosomes = new HashSet<String>()
    
    RegionValue(String value) {
        this.value = value
        initId()
    }
    
    RegionValue(Iterable<Sequence> sequences) {
        this.value = sequences.collect { Sequence s -> "$s.name:$s.range.from-$s.range.to" }.join(" ")
        int minFrom = Integer.MAX_VALUE
        int maxTo = Integer.MIN_VALUE
        for(Sequence s : sequences) {
            if(s.range.from < minFrom)
                minFrom = (int)s.range.from
            if(s.range.to > maxTo)
                maxTo = (int)s.range.to
            this.chromosomes.add(s.name)
            this.totalSize += (s.range.to - s.range.from)
        }
        this.rangeFrom = (minFrom == Integer.MAX_VALUE) ? 0 : minFrom
        this.rangeTo = (maxTo == Integer.MIN_VALUE) ? 0 : maxTo
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
           return "{region:$fn.path}"
       } 
    }
    
    String bedFlag(String flag) {
        if(this.isEmpty()) {
            return ""
        }
       File fn = getBedFile()
       return "$flag {region:$fn.path}"
    }
    
    private File getBedFile() {
        
        // Here this needs to use the storage from the executor to connect this to a Path
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
    
    int getFrom() {
        return rangeFrom
    }
    
    int getTo() {
        return rangeTo
    }
    
    int size() {
        return this.totalSize
    }
}
