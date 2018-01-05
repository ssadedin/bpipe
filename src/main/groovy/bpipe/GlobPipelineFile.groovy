/*
 * Copyright (c) Murdoch Childrens Research Institute and Contributers
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * 
 * Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */  

package bpipe

import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import groovy.transform.CompileStatic

/**
 * A pipeline file whose name is a pattern which maps to any file 
 * matching the pattern.
 * <p>
 * This class supports both regex style patterns and file system globs.
 * 
 * @author simon.sadedin
 */
class GlobPipelineFile extends PipelineFile {
    
    Pattern pattern
    
    GlobPipelineFile(String pattern) {
        super(pattern)
    }
    
    GlobPipelineFile(Pattern pattern) {
        super(pattern.toString())
        this.pattern = pattern
    } 
    
    @Override
    boolean exists() {
        return getDirectoryStream().any { true }
    }
    
    @CompileStatic
    List<Path> toPaths() {
        List<Path> paths = (List<Path>)getDirectoryStream().collect { it }
        if(paths.isEmpty())
            return [this.toPath()]
        else
            return paths
    }
    
    DirectoryStream getDirectoryStream() {
       Path myPath = this.toPath()
       Path dir = resolveDir(myPath)
       if(!Files.exists(dir)) {
           Files.createDirectories(dir)
       }
       
       if(pattern != null) {
           return Files.newDirectoryStream(dir, new DirectoryStream.Filter<Path>() {
               boolean accept(Path path) {
                   path.fileName.toString() ==~ pattern
               }
           })
       }
       else {
           return Files.newDirectoryStream(dir, myPath.fileName.toString()) 
       }
    }
    
    Path resolveDir(Path child) {
        Path dir = child.parent
        if(dir == null) { // unqualified path
            dir = new File(".").toPath()
        }
        return dir
    }
}
