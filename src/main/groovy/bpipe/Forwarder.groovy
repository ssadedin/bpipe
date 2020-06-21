/*
* Copyright (c) 2012 MCRI, authors
*
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
*
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/
package bpipe

import java.nio.file.*
import java.util.logging.Level;

import groovy.transform.CompileStatic
import groovy.util.logging.Log

/**
 * Continuously monitors files and forwards (or 'tails') their outputs to 
 * specific destinations.  The main purpose of this class is to avoid
 * having all the files be continuously open which is necessary when there
 * are limits on the number of open files.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Log
class Forwarder extends TimerTask {
    
    /**
     * Global list of all forwarders
     */
    static List<Forwarder> forwarders = []
    
    /**
     * Timer that schedules polling of files that are forwarded from Torque jobs
     */
    static Timer forwardingTimer
    
    /**
     * Longest amount of time we will wait for an expected file that does not exist
     * to appear
     */
    static long MAX_WAIT_MISSING_FILE = 10000
    
    
    /**
     * The list of files that are being 'tailed'
     */
    List<Path> files = []
    
    /**
     * Current position in each file that we know about
     */
    Map<Path, Long> filePositions = [:]
    
    /**
     * Destinations to which the file should be forwarded
     */
    Map<Path, Appendable> fileDestinations = [:]
    
    Forwarder(File f, Appendable out) {
        forward(f,out)
    }
    
    Forwarder(Path f, Appendable out) {
        forward(f,out)
    }
 
    void forward(File file, Appendable out) {
        Path path = file.toPath()
        forward(path, out)
    }
    
    void forward(Path path, Appendable out) {
        synchronized(files) {
            files << path
            fileDestinations[path] = out
            filePositions[path] = Files.exists(path) ? Files.size(path) : 0L
        }
    }    
    
    void stopForwarding() {
        synchronized(files) {
            this.files.clear()
            this.fileDestinations.clear()
            this.filePositions.clear()
        }
    }
	
    void cancelFile(File file) {
        synchronized(files) {
            Path p = file.toPath()
            files.removeIf { Files.isSameFile(p, it) }
            fileDestinations.removeAll {  Files.isSameFile(p, it.key) }
            boolean removed = filePositions.removeAll {  Files.isSameFile(p, it.key) }
        }
    }
    
    /**
     * Attempt to wait until all the expected files exist, then run forwarding
     */
    @CompileStatic
    public void flush() {
        synchronized(files) {
            // This small wait is to allow a small window for flushable changes to appear in the files
            Thread.sleep(200)
            
            long startTimeMs = System.currentTimeMillis()
            long now = startTimeMs
            
            // This is done to synchronized distributed file systems with sync issues
            this.files.collect { it.toAbsolutePath().parent }.unique { it.normalize() }.collect { dir -> 
                DirectoryStream dirStream = Files.newDirectoryStream(dir)
                try {
                    dirStream.toList() 
                }
                finally {
                    dirStream.close()
                }
            }
            
            while(now - startTimeMs < MAX_WAIT_MISSING_FILE) {
                if(this.files.every { Files.exists(it) })
                    break
                now = System.currentTimeMillis()
                Thread.sleep(1000)
            }
            if(now - startTimeMs >= MAX_WAIT_MISSING_FILE) {
                def msg = "Exceeded $MAX_WAIT_MISSING_FILE ms waiting for one or more output files ${files*.toAbsolutePath()} to appear: output may be incomplete"
                System.err.println  msg
                log.warning msg
            }
            else {
                log.info "All files ${files*.toAbsolutePath()} exist"
            }
        }
        
        while(true) {
            if(this.scanFiles()) { // returns true if one or more files modified; in that case keep looping
                Thread.sleep(200)
            }
            else
                break
        }
    }
    
    @Override
    public void run() {
        scanFiles()
    }
    
    /**
     * Scan all the files known by this forwarder
     * 
     * @return  true if any new content observed, false otherwise
     */
    @CompileStatic
    boolean scanFiles() {
        
        boolean modified = false
        
        synchronized(files) {
            try {
                Collection<Path> scanFiles = files.grep { Files.exists((Path)it) }
                byte [] buffer = new byte[8096]
                if(log.isLoggable(Level.FINE)) 
                    log.fine "Scanning ${scanFiles.size()} / ${files.size()} files "
                    
                for(Path p in scanFiles) {
                    try {
                        Files.newInputStream(p).withStream { InputStream ifs ->
                            modified = updateFileContent(p, ifs, buffer, modified)
                        }
                    }
                    catch(Exception e) {
                        log.log Level.WARNING, "Unable to read file $p", e
                    }
                }
            }
            catch(Exception e) {
                log.log(Level.WARNING, "Failure in output forwarding", e)
            }
        }
        return modified
    }

    @CompileStatic
    private boolean updateFileContent(Path p, InputStream ifs, byte[] buffer, boolean modified) {
        long skip = filePositions[p]
        ifs.skip(skip)
        int count = ifs.read(buffer)
        if(count < 0) {
            //log.info "No chars to read from ${f.absolutePath} (size=${f.length()})"
            return false
        }

        modified = true

        // TODO: for neater output we could trim the output to the
        // most recent newline here
        String content = new String(buffer,0,count)
        
        log.info "Read " + count + " chars from $p starting with " + Utils.truncnl((String)content.take(30),25)
        
        Appendable dest = fileDestinations[p]
        dest.append(content)
        if(dest instanceof Flushable) {
            ((Flushable)dest).flush()
        }
        else
        if(dest instanceof OutputLog) {
            ((OutputLog)dest).flush()
        }
            
        filePositions[p] = filePositions[p] + count

        return modified
    }
}
