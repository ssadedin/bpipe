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

import groovy.transform.CompileStatic
import groovy.util.logging.Log
import groovyx.gpars.actor.Actors
import static groovyx.gpars.actor.Actors.actor

import static bpipe.CommandManager.UNCLEAN_FILE_DIR

/**
 * Manages recording of dirty state of files, and cleaning up dirty resources
 * <p>
 * There are two records of files that are considered "in progress" or possibly not
 * having final status. These are:
 * 
 * <li>unclean files  - outputs of in progress commands
 * <li>dirty files    - files that could not be cleaned up even though they were the result
 *                      of failed commands
 *                      
 * @author Simon Sadedin
 */
@Singleton()
@Log
@CompileStatic
class DirtyFileManager {
    
    final public static File DIRT_FILE = new File(".bpipe/dirty.txt")
    
    List dirtyFiles = []
    
    void add(List<PipelineFile> files) {

        // Store the list of output files so that if we are killed 
        // they can be cleaned up
        
        String fileList = files?.join("\n") + '\n'
        
        File uncleanFilePath = this.getUncleanFile()
        
        if(!uncleanFilePath.exists()) {
            uncleanFilePath.text = fileList 
        }
        else {
            uncleanFilePath.append(fileList)
        }
    }
    
    void clear() {
        getUncleanFile().delete()
    }
    
    /**
     * First delete and then initialize with blank contents the list of 
     * unclean files
     */
    File initUncleanFilePath() {
        if(!UNCLEAN_FILE_DIR.exists()) 
            UNCLEAN_FILE_DIR.mkdirs()
            
        File uncleanFilePath = getUncleanFile()   
        if(uncleanFilePath.exists())
            uncleanFilePath.delete()
        uncleanFilePath.text = ""
        
        return uncleanFilePath
    }
    
    File getUncleanFile() {
        new File(UNCLEAN_FILE_DIR, String.valueOf(Thread.currentThread().id))        
    }
    
    /**
     * 
     * @param outputDirectory
     * @param outputFiles
     */
    void cleanup(String outputDirectory, List<PipelineFile> outputFiles) {

        OutputDirectoryWatcher odw 
        if(outputDirectory != null) {
            odw = OutputDirectoryWatcher.getDirectoryWatcher(outputDirectory)
            odw.sync()
        }
         
        // Out of caution we don't remove output files if they existed before this stage ran.
        // Otherwise we might destroy existing data
        def newOutputFiles = Utils.box(outputFiles).collect { it.toString() }.unique()
        
        log.info "Identified ${newOutputFiles.size()} cleanup candidates: " + newOutputFiles

        newOutputFiles.removeAll { fn ->
            boolean keep = odw?.isPreexisting(fn)
            if(keep)
                log.info "Keeping $fn because determined as pre-existing"
            return keep;
        }

        log.info("Cleaning up ${newOutputFiles.size()} files: $newOutputFiles")
        List<String> failed = Utils.cleanup(newOutputFiles)
        if(failed) {
            DirtyFileManager.instance.markDirty(failed)
        }
    }
    
    /**
     * Record the given file as dirty - ie: should be cleaned up.
     * <p>
     * This only happens when a file in unclean state cannot be deleted
     */
    synchronized void markDirty(List<String> files) {
        files.collect { new File(it).canonicalFile }.each { File file ->
            List<String> dirtyFiles = []
            if(DIRT_FILE.exists()) {
                dirtyFiles = DIRT_FILE.readLines() 
            }
            dirtyFiles.add(file.absolutePath)
            DIRT_FILE.text = dirtyFiles.join("\n")
        }
    }

     /**
     * Check for any files that were marked dirty but could not be actively cleaned up 
     * during the pipeline run. We will make one more attempt to clean them up here,
     * and if that is not possible, print a verbose error for the user.
     * 
     * @return
     */
    void cleanupDirtyFiles() {
        List<String> dirtyFiles = []
        if(DIRT_FILE.exists()) {
            dirtyFiles = DIRT_FILE.readLines() 
        }        
        List<File> uncleanFileManifests = getUncleanManifests()
        List<String> uncleanFilePaths = getUncleanFilePaths()
        
        log.info "Cleanup required for ${dirtyFiles.size()} dirty files and ${uncleanFilePaths.size()} unclean files"
       
        List<String> failedFiles = []
        (dirtyFiles + uncleanFilePaths).each { String file ->
            List failed 
            for(int i=0; i<3; ++i) {
                failed = Utils.cleanup(file)  
                if(!failed)
                    break
                println "Cleanup of $file failed: retry $i"
                Thread.sleep(500)
            }
            
            if(failed) {
                failedFiles.addAll(failed)
            }
        }
        
        if(failedFiles) {
            int cols = (int)Config.config.columns
            println " Warning: Cleanup Failures Occurred ".center(cols,"=")
            failedFiles.each { file ->
                println(("| " + file).padRight(cols-1)+"|")
            }
            println "=" * cols
        }
        else {
            uncleanFileManifests.each { File f ->
                f.delete()
            }
            DIRT_FILE.renameTo(new File(".bpipe/dirty.last.txt"))
        }
    }
    
    List<File> getUncleanManifests() {
        (CommandManager.UNCLEAN_FILE_DIR.listFiles()?:[]) as List<File>
    }
    
    List<String> getUncleanFilePaths() {
        (getUncleanManifests().collect {
                ((File)it).readLines()*.trim().grep { it }
        }
        .sum()?:[]) as List<String>
    }
    
    
    /**
     * Clear any previous state about dirty files from previous runs and
     * set the flag that indicates if new dirty files appear, they need
     * cleanup processing
     */
    void initCleanupState() {

        Runner.cleanupRequired = true
        
        List<File> filesToClear = []
        
        if(!CommandManager.UNCLEAN_FILE_DIR.exists())        
            return
            
        try {
            CommandManager.UNCLEAN_FILE_DIR.eachFileMatch(~'[0-9]{1,}') {
                filesToClear << it
            }
            
            log.info "Clearing ${filesToClear.size()} existing inprogress files"
            
            filesToClear*.delete()        
        }
        catch(Exception e) {
            log.warning "Unable to initialise cleanp processing: dirty files may carry over from previous run ($e)"
        }
    }
    
    @CompileStatic
    static DirtyFileManager getTheInstance() {
        return DirtyFileManager.instance
    }
}
