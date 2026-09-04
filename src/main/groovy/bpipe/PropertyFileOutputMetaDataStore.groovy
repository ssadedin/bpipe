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

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log

/**
 * A {@link OutputMetaDataStore} implementation that persists metadata to
 * individual {@code .properties} files in {@code .bpipe/outputs/}.
 * <p>
 * This is the legacy backend, wrapping the existing I/O logic from
 * {@link OutputMetaData#save()} / {@link OutputMetaData#read()} / 
 * {@link Dependencies#scanOutputFolder()} without changing semantics.
 * <p>
 * Once the SQLite backend is stable this class will be retained as the
 * migration source and as a configurable fallback.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Log
@CompileStatic
class PropertyFileOutputMetaDataStore implements OutputMetaDataStore {
    
    @Override
    void save(OutputMetaData p) {
        p.save()
    }
    
    @Override
    void saveAll(List<OutputMetaData> items) {
        for(OutputMetaData p in items) {
            p.save()
        }
    }
    
    @Override
    void update(OutputMetaData p) {
        p.save()
    }
    
    @Override
    void load(OutputMetaData p) {
        p.read()
    }
    
    @Override
    List<OutputMetaData> loadAll() {
        scanOutputFolder()
    }
    
    @Override
    boolean exists(OutputMetaData p) {
        p.exists()
    }
    
    @Override
    void flush() {
        // no-op: property files are written synchronously
    }
    
    @Override
    void close() {
        // no-op: no resources to release
    }
    
    /**
     * Scan the .bpipe/outputs directory for property files and load them.
     * <p>
     * This is the same logic that formerly lived in
     * {@link Dependencies#scanOutputFolder()} and
     * {@link Dependencies#isOutputMetaFile(File)}.
     */
    @CompileStatic(TypeCheckingMode.SKIP)
    private List<OutputMetaData> scanOutputFolder() {
        int concurrency = (int)(Config.userConfig?.getOrDefault('outputScanConcurrency',5)?:5)
        List result = []
        Utils.time("Output folder scan (concurrency=$concurrency)") {
            
            List<File> files = 
               (List)new File(OutputMetaData.OUTPUT_METADATA_DIR)
                   .listFiles()
                   .findAll { isOutputMetaFile(it) } 
                                
            if(files.isEmpty())
                return files
                    
            groovyx.gpars.GParsPool.withPool(concurrency) { 
                result.addAll(files.collectParallel { File f ->
                    OutputMetaData.fromFile(f)
                }.grep { it != null }.sort { it.timestamp })
            }
        }
        return result
    }
    
    /**
     * Return true if a file could be a valid output property file
     * <p>
     * Ignores files starting with ., added as a convenience because I occasionally
     * edit files in output folder when debugging, and known files in the output folder that
     * are not meta files.
     */
    @CompileStatic
    static boolean isOutputMetaFile(File file) {
        !file.name.startsWith(".") && !file.isDirectory() &&
        !file.name.equals("outputGraph.ser") && !file.name.equals("outputGraph2.ser") &&
        !file.name.equals("outputs.db")
    }
}