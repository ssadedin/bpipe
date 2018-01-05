package bpipe

import java.nio.file.Path
import java.nio.file.Files
import java.util.regex.Pattern

import bpipe.storage.StorageLayer
import groovy.transform.CompileStatic

class FileGlobber {
    
    StorageLayer storage 
    
    List<String> glob(String globPattern) {
        // Determine the base directory that will be searched
        Path patternPath = storage.toPath(globPattern.toString())
        Path dir = resolveDir(patternPath)
        
        List<String> result = 
            Files.newDirectoryStream(dir, patternPath.fileName.toString()).collect { Path p ->
                p.normalize().toString()
            }
            
        return result
    }
    
    @CompileStatic
    List<String> glob(Pattern globPattern) {
        
        // Determine the base directory that will be searched
        Path patternPath = storage.toPath(globPattern.toString())
        Path dir = resolveDir(patternPath)
        
        Pattern pattern = Pattern.compile(patternPath.fileName.toString())
        
        List<String> result = Files.newDirectoryStream(dir).grep { Path p ->
                pattern.matcher(p.fileName.toString()).matches() 
            }.collect { p -> 
                ((Path)p).normalize()
            }*.toString()
            
        return result
    }
    
    Path resolveDir(Path child) {
        Path dir = child.parent
        if(dir == null) { // unqualified path
            dir = new File(".").toPath()
        }
        return dir
    }
    
    @CompileStatic
    List<String> cleanupResult(List<String> results) {
        results.collect { 
            it.startsWith('./') ? it.substring(2) : it
        }
    }
}
