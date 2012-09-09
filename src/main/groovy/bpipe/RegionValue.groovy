package bpipe

/**
 * A wrapper for either a RegionSet or a Chr that
 * enables them to be used as either string values or to support a ".bed"
 * extension.
 */
class RegionValue {
    
    String value
    
    def propertyMissing(String name) {
       if(name == "bed") {
           def fn = new File(".bpipe",Utils.sha1(value))
           if(!fn.exists()) {
               fn.text = value.replaceAll("-","\t").replaceAll(":","\t").split(" ").join("\n")
           }
           return fn.absolutePath
       } 
    }
    
    String toString() {
        return value   
    }
}
