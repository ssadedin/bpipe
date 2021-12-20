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
    
    private HashMap<String,List<String>> aliasTargets = [:]
    
    synchronized boolean isAliased(String path) {
        aliases.containsKey(path) || aliasTargets.containsKey(path)
    }
    
    synchronized boolean isAliased(PipelineFile file) {
        isAliased(file.path)
    } 
    
    @CompileStatic
    synchronized add(String fromPath, PipelineFile toFile) {
        assert fromPath != null
        assert toFile != null
        aliases[fromPath] = new AliasMapping(from:fromPath, to:toFile)
        aliasTargets.get(toFile.toString(),(List<String>)[]).add(fromPath)
    }
    
    @CompileStatic
    List<String> getMappings(PipelineFile fromFile) {
        aliasTargets[fromFile.toString()]
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
