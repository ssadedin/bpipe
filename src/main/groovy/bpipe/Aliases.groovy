package bpipe

import groovy.transform.CompileStatic;

class Aliases {
    
    Map<String,String> aliases = [:]
    
    synchronized add(String fromPath, String toPath) {
        aliases[fromPath] = toPath
    }
    
    @CompileStatic
    synchronized String getAt(String fromPath) {
        String result = aliases[fromPath]
        if(result)
            return result
        return fromPath
    }
}
