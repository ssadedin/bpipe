package bpipe.agent

import groovy.util.logging.Log;

@Log
class PipelineInfo {
    
    PipelineInfo(File jobFile) {
        List lines = jobFile.readLines()*.trim()
        this.command = lines[0]
        
        int infoStart = lines.findIndexOf { it.startsWith("--------") }
        Map info = lines[(infoStart+1)..-1].collectEntries {
            it.split(":")*.trim()
        }
        
        this.pguid = info.pguid
        this.pid = jobFile.name
        this.path = jobFile.absoluteFile.parentFile.parentFile.parentFile.canonicalPath
    }
    
    String command
    
    String pguid
    
    String pid
    
    String path
}

