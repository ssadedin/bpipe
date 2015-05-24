package bpipe

import groovy.transform.CompileStatic;

class Aliases {
    
    private Map<String,String> aliases = [:]
    
    private HashSet<String> aliasTargets = new HashSet()
    
    synchronized boolean isAliased(String path) {
        aliases.containsKey(path) || aliasTargets.contains(path)
    }
    
    synchronized add(String fromPath, String toPath) {
        aliases[fromPath] = toPath
        aliasTargets.add(toPath)
    }
    
    @CompileStatic
    synchronized String getAt(String fromPath) {
        String result = aliases[fromPath]
        if(result)
            return result
        return fromPath
    }
    
    synchronized String toString() {
        aliases.toString()
    }
}
