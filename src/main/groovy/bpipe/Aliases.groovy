package bpipe

import groovy.transform.CompileStatic;
import groovy.transform.ToString

@ToString
class AliasMapping {
    String from
    PipelineFile to
}

class Aliases {
    
    /**
     * Map of alias fromPath => toPath
     */
    private Map<String,AliasMapping> aliases = [:]
    
    private HashSet<String> aliasTargets = new HashSet()
    
    synchronized boolean isAliased(String path) {
        aliases.containsKey(path) || aliasTargets.contains(path)
    }
    
    @CompileStatic
    synchronized add(String fromPath, PipelineFile toFile) {
        assert fromPath != null
        assert toFile != null
        
        aliases[fromPath.toString()] = new AliasMapping(from:fromPath, to:toFile)
        aliasTargets.add(toFile.toString())
    }
    
    @CompileStatic
    synchronized PipelineFile getAt(PipelineFile fromPath) {
        PipelineFile result = aliases[fromPath.toString()]?.to
        if(result)
            return result
        return fromPath
    }
    
    synchronized String toString() {
        aliases.toString()
    }
}
