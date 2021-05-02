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
    
    TreeMap<Long,List<String>> timestamps = new TreeMap()
    
    Map<String,Long> files = new HashMap()
    
    Set<String> createdFiles = new HashSet()
    
    List<List> fileCreationListeners = []
    
    boolean stop = false
    
    long initTimeMs = -1L
    
    /**
     * Global registry of directory watchers - we keep it to 1 per directory by
     * registering them here and gating access through the getDirectoryWatcher 
     * method.
     */
    private static Map<String,OutputDirectoryWatcher> watchers = [:]
    
    OutputDirectoryWatcher(String directory) {
        this(new File(directory).toPath())
    } 
    
    OutputDirectoryWatcher(Path directory) {
        this.directory = directory
        if(!Files.exists(directory))
            directory.toFile().mkdirs()
            
        if(!Config.userConfig.getOrDefault('usePollerFileWatcher', false)) {
            this.watcher = FileSystems.getDefault().newWatchService();
        }
    }
    
    @Override
    public void run() {
        
        if(Config.userConfig.usePollerFileWatcher) {
            log.info "Watching directories using manual poller"
            runUsingManuallPoller()
        }
        else {
            log.info "Watching directories using native watcher"
            runUsingNativeWatcher()
        }
    }
    
    Object manualPollerWaitLock = new Object()
    
    @CompileStatic
    void runUsingManuallPoller() {
        
        this.initialize()
        
        long manualPollerSleepTime = (long)Config.userConfig.getOrDefault("manualPollerSleepTime",30000)
        
        Map<String,Long> oldPaths = NewFileFilter.scanOutputDirectory(directory.toFile().path, null).collectEntries { Path p ->
            [p.fileName.toString(), Files.getLastModifiedTime(p).toMillis()]
        }
        
        log.info "Found existing paths: " + oldPaths
        
        for(;;) {
            synchronized(manualPollerWaitLock) {
                manualPollerWaitLock.wait(manualPollerSleepTime) 
            }
            
            List<Path> newPaths = NewFileFilter.scanOutputDirectory(directory.toFile().path, oldPaths)
            
            for(Path newPath in newPaths) {
                log.info "Manual poller detected $newPath.fileName"
                this.processEvent(ENTRY_CREATE, newPath.fileName)
            }
            
            // trigger notification for any threads sync() methods
            // waiting for files to appear
            synchronized(timestamps) {
                timestamps.notify()
            }
            
            long nowMs = System.currentTimeMillis()
            oldPaths += newPaths.collectEntries { p ->
                try {
                    [p.fileName.toString(), Files.getLastModifiedTime(p).toMillis()]
                }
                catch(java.nio.file.NoSuchFileException e) {
                    log.info "File $p.fileName removed after detection?"
                    [p.fileName.toString(), nowMs]
                }
            }
        }
    }
    
    @CompileStatic
    void runUsingNativeWatcher() {
        
        this.setupWatcher()
            
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
                
                Thread.sleep(1000)
                if(!setupWatcher(false)) // one reason this can happen is if the directory 
                                         // was deleted deliberately. If so, don't recreate
                    return
            }
        }
    }

    private boolean setupWatcher(boolean mkdir=true) {
        
        try {
            if(!Files.exists(this.directory) && mkdir)
                this.directory.toFile().mkdirs()

            this.watchKey =
                    this.directory.register(watcher, [ENTRY_CREATE, ENTRY_MODIFY] as WatchEvent.Kind[],  com.sun.nio.file.SensitivityWatchEventModifier.HIGH)

            log.info "Using high sensitivity file watcher for $directory"
            
            return true
        }
        catch(Throwable t) {
            log.info "Fall back to low sensitivity file watching for $directory"
            
            try {
                this.watchKey =
                        this.directory.register(watcher, [ENTRY_CREATE, ENTRY_MODIFY] as WatchEvent.Kind[])
            }
            catch(Throwable t2) {
                log.warning "Unable to create directory watcher: " + t2
                return false
            }
        }
    }
    
    @CompileStatic
    void processEvent(WatchEvent.Kind kind, Path path) {
        
        Path resolvedPath = this.directory.resolve(path)
        if(!Files.exists(resolvedPath)) {
            log.warning "Watcher notified about path $resolvedPath which doesn't exist: ignoring" 
            return
        }
        
        long timestamp = (long)Utils.withRetries(2) {  
            Files.getLastModifiedTime(resolvedPath).toMillis()
        }
        
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

            // log.info "Path $path was updated / created"        
        }
    }
    
    @CompileStatic
    void initialize() {
        List<Path> oldPaths = NewFileFilter.scanOutputDirectory(directory.toFile().path, null)
        log.info "Initialising watcher for $directory with ${oldPaths.size()} paths"
        synchronized(timestamps) {
            for(Path path in oldPaths) {
                long timestamp = Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : 0L
                String fileName = path.fileName
                List<String> tsPaths = this.timestamps[timestamp] 
                if(!tsPaths)
                    this.timestamps[timestamp] = [fileName]
                else
                    tsPaths.add(fileName)
                    
                files[fileName] = timestamp
            }
        }
        this.initTimeMs = System.currentTimeMillis()
    }
    
    @CompileStatic
    long timestampOf(String fileName) {
        synchronized(timestamps) {
            return files[fileName]
        }
    }
    
    @CompileStatic
    long maxTimestamp() {
        synchronized(timestamps) {
            Long ts = this.timestamps.lastKey()
            return ts == null ? -1L : ts.longValue()
        }
    }
    
    @CompileStatic
    Map<String, Long> modifiedSince(long timeMs) {
        
        long thresholdTimeMs = (long)(timeMs/1000L)* 1000L

        Map<String,Long> results = [:]

        synchronized(this.timestamps) {
            Map.Entry<Long,List<String>> entry
            long entryTimeMs = Math.max(0,thresholdTimeMs-1)
            while(entry = this.timestamps.higherEntry(((Long)entryTimeMs))) {
                for(String path in entry.value) {
                    results[path] = entry.key
                }
                entryTimeMs = entry.key
            }
        }
        return results
    }
    
    @CompileStatic
    synchronized public static OutputDirectoryWatcher getDirectoryWatcher(String forDirectory) {
        OutputDirectoryWatcher watcher = watchers[forDirectory]
        if(watcher == null) {
            log.info "Creating directory watcher for $forDirectory"
            watcher = new OutputDirectoryWatcher(forDirectory)
            watcher.setDaemon(true)
            watcher.start()
            watchers[forDirectory] = watcher
        }
        return watcher
    }
    
    static final long SYNC_TIMEOUT_MS = 30000
    
    /**
     * Sleep until we have high confidence that this directory watcher has received
     * all notifications for its directory. 
     * <p>
     * This is achieved by actually creating a file and waiting for the notification
     * of that file to be received.
     */
    @CompileStatic
    void sync() {
        
        // create tmp file
        // wait until we are notified about it
        // return 
        int rand = new Random().nextInt()
        File directoryFile = directory.toFile()
        if(!directoryFile.exists()) {
            log.warning("Cannot sync directory $directoryFile because it doesn't exist")
            return
        }
            
        File tmpFile = new File(directoryFile,".bpipe.tmp-"+rand)
        tmpFile.text = ""
        
        long startTimeMs = System.currentTimeMillis()
        boolean created = createdFiles.contains(tmpFile.name)
        synchronized(manualPollerWaitLock) {
            manualPollerWaitLock.notify()
        }
        
        while(!created) {
            synchronized(this.timestamps) {
                this.timestamps.wait(300)
                created = createdFiles.contains(tmpFile.name)
            }
            if(System.currentTimeMillis()-startTimeMs > SYNC_TIMEOUT_MS) {
                log.warning("File system sync timed out after " +  (System.currentTimeMillis()-startTimeMs) + "ms: file $tmpFile was not observed as created")
                break
            }
        }
        tmpFile.delete()
    }
    
    @CompileStatic
    boolean isPreexisting(String fileName) {
        synchronized(this.timestamps) {
            String normalisedFileName = new File(fileName).name
            boolean created = this.createdFiles.contains(normalisedFileName)
            if(created)
                return false
                
            Long ts = files[fileName]
            if(ts == null) // If it existed previously then it would have been populated in 
                           // the files index upon initialization
                return false
            
            // If it got populated, but after initialization then it must not be pre-existing
            return ts > this.initTimeMs
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
