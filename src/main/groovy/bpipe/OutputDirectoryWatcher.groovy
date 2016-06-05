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

import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files;
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent
import java.nio.file.WatchKey;
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

import com.sun.nio.file.SensitivityWatchEventModifier;

import groovy.transform.CompileStatic;
import groovy.util.logging.Log;

import static java.nio.file.StandardWatchEventKinds.*

@Log
class OutputDirectoryWatcher extends Thread {
    
    Path directory
    
    WatchKey watchKey
    
    WatchService watcher 
    
    Map<Long,List<String>> timestamps = new TreeMap()
    
    Map<String,Long> files = new HashMap()
    
    Set<String> createdFiles = new HashSet()
    
    List<List> fileCreationListeners = []
    
    boolean stop = false
    
    /**
     * Global registry of directory watchers - we keep it to 1 per directory by
     * registering them here and gating access through the getDirectoryWatcher 
     * method.
     */
    private static Map<String,OutputDirectoryWatcher> watchers = [:]
    
    OutputDirectoryWatcher(String directory) {
        this.directory = new File(directory).toPath()
        this.watcher = FileSystems.getDefault().newWatchService();
    } 
    
    OutputDirectoryWatcher(Path directory) {
        this.directory = directory
        this.watcher = FileSystems.getDefault().newWatchService();
    }
    
    @Override
    public void run() {
        try {
            this.watchKey = 
                this.directory.register(watcher, [ENTRY_CREATE, ENTRY_MODIFY] as WatchEvent.Kind[],  com.sun.nio.file.SensitivityWatchEventModifier.HIGH)
            log.info "Using high sensitivity file watcher for $directory"
        }
        catch(Throwable t) {
            log.info "Fall back to low sensitivity file watching for $directory"
            this.watchKey = 
                this.directory.register(watcher, [ENTRY_CREATE, ENTRY_MODIFY] as WatchEvent.Kind[])
        }
            
        this.initialize()
        
        for(;;) {
            
            log.fine "Polling directory $directory"
            
            WatchKey key = watcher.poll(2000, TimeUnit.MILLISECONDS)
            
            if(stop) {
                log.info "Stopping polling of directory $directory"
                return
            }
            
            if(key == null)
                continue
            
            assert key == this.watchKey
            
            for(WatchEvent<Path> e in key.pollEvents()) {
                
                WatchEvent.Kind kind = e.kind()
                if(kind == OVERFLOW) {
                    log.warning "Overflow of directory watcher for $directory occurred!"
                    continue
                }
                
                Path path = e.context()
                processEvent(kind, path)
            }
            
            // trigger notification for any threads sync() methods
            // waiting for files to appear
            synchronized(timestamps) {
                timestamps.notify()
            }
            
            if(!key.reset()) {
                log.warning("WARNING: watch key for directory $directory expired")
                return
            }
        }
    }
    
    void processEvent(WatchEvent.Kind kind, Path path) {
        long timestamp = Files.getLastModifiedTime(this.directory.resolve(path)).toMillis()
        synchronized(timestamps) {
            // Known file?
            String fileName = path.fileName.toString()
            Long oldTimestamp = this.files[fileName]
            if(oldTimestamp != null) {
                List filesWithTimestamp = timestamps[oldTimestamp]
                if(filesWithTimestamp != null) {
                    filesWithTimestamp.remove(fileName)
                    if(filesWithTimestamp.isEmpty()) {
                        timestamps.remove(oldTimestamp)
                    }
                }
                else {
                    log.warning("Directory $directory index has inconsistent state")
                }
            }
                    
            // Update timestamp index
            List<String> filesWithTimestamp = this.timestamps[timestamp]
            if(filesWithTimestamp != null) {
                filesWithTimestamp.add(fileName)
            }
            else {
                this.timestamps[timestamp] = [fileName]
            }
            
            if(kind == ENTRY_CREATE) {
                log.info "File $path was created in directory $directory"
                createdFiles.add(fileName)
                fileCreationListeners*.add(path)
            }
                        
            // Update filename index
            files[fileName] = timestamp
            log.info "Path $path was updated / created"        
        }
    }
    
    @CompileStatic
    void initialize() {
        List<Path> oldPaths = NewFileFilter.scanOutputDirectory(directory.toFile().path, null)
        
        log.info "Initialising watcher for $directory with ${oldPaths.size()} paths}"
        for(Path path in oldPaths) {
            long timestamp = Files.getLastModifiedTime(path).toMillis()
            String fileName = path.fileName
            List<String> tsPaths = this.timestamps[timestamp] 
            if(!tsPaths)
                this.timestamps[timestamp] = [fileName]
            else
                tsPaths.add(fileName)
        }
    }
    
    long timestampOf(String fileName) {
        synchronized(timestamps) {
            return files[fileName]
        }
    }
    
    long maxTimestamp() {
        synchronized(timestamps) {
            return timestamps.lastKey()
        }
    }
    
//    @CompileStatic
    Map<String, Long> modifiedSince(long timeMs) {
        Map<String,Long> results = [:]
        synchronized(this.timestamps) {
            Map.Entry<Long,List<String>> entry
            long entryTimeMs = timeMs
            while(entry = this.timestamps.higherEntry(((Long)entryTimeMs))) {
                for(String path in entry.value) {
                    results[path] = entry.key
                }
                entryTimeMs = entry.key
            }
        }
        return results
    }
    
    synchronized public static OutputDirectoryWatcher getDirectoryWatcher(String forDirectory) {
        OutputDirectoryWatcher watcher = watchers[forDirectory]
        if(watcher == null) {
            watcher = new OutputDirectoryWatcher(forDirectory)
            watcher.setDaemon(true)
            watcher.start()
            watchers[forDirectory] = watcher
        }
        return watcher
    }
    
    /**
     * Sleep until we have high confidence that this directory watcher has received
     * all notifications for its directory. 
     * <p>
     * This is achieved by actually creating a file and waiting for the notification
     * of that file to be received.
     */
    void sync() {
        // create tmp file
        // wait until we are notified about it
        // return 
        int rand = new Random().nextInt()
        File tmpFile = new File(directory.toFile(),".bpipe.tmp-"+rand)
        tmpFile.text = ""
        boolean created = createdFiles.contains(tmpFile.name)
        while(!created) {
            synchronized(this.timestamps) {
                this.timestamps.wait(300)
                created = createdFiles.contains(tmpFile.name)
            }
        }
        tmpFile.delete()
    }
    
    @CompileStatic
    boolean isPreexisting(String fileName) {
        synchronized(this.timestamps) {
            boolean created = this.createdFiles.contains(fileName)
            return !created;
        }
    }
    
    static int countGlobalGlobMatches(List<String> globs) {
        List<Pattern> patterns = globs.collect { FastUtils.globToRegex(it) }
        return countGlobalPatternMatches(patterns)
    }
    
    /**
     * Return the total count of files matching the given glob observed
     * in ALL output directories
     * 
     * @param glob
     * @return
     */
    @CompileStatic
    static int countGlobalPatternMatches(List<Pattern> patterns) {
        int result = 0
        for(Map.Entry<String,OutputDirectoryWatcher> watcher in watchers) {
            synchronized(watcher.value.timestamps) {
                for(String key in watcher.value.files.keySet()) {
                    for(Pattern pattern in patterns) {
                        if(key.matches(pattern))    {
                            ++result
                        }
                    }
                }
            }
        }
        return result
    }
}
