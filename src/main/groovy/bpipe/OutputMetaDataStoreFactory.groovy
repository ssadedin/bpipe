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
import groovy.util.logging.Log

/**
 * Factory for creating the appropriate {@link OutputMetaDataStore} implementation
 * based on the user's configuration.
 * <p>
 * Reads {@code outputMetaData.backend} from the Bpipe config (default: {@code "sqlite"}).
 * If the SQLite backend is selected and the database does not yet exist but legacy
 * property files are found, an automatic one-time migration is performed.
 * 
 * @see OutputMetaDataStore
 * @see SQLiteOutputMetaDataStore
 * @see PropertyFileOutputMetaDataStore
 */
@Log
@CompileStatic
class OutputMetaDataStoreFactory {
    
    /**
     * Create and return the appropriate {@link OutputMetaDataStore} based on config.
     * <p>
     * If the SQLite backend is selected but no database file exists yet and
     * property files are present, a one-time migration imports the existing
     * metadata into the database before returning.
     * 
     * @return a configured, ready-to-use store
     */
    static OutputMetaDataStore create() {
        String backend = determineBackend()
        
        log.info "Creating output metadata store with backend: $backend"
        
        if(backend == 'properties') {
            return new PropertyFileOutputMetaDataStore()
        }
        
        // SQLite backend
        
        File outputsDir = new File(OutputMetaData.OUTPUT_METADATA_DIR)
        File dbFile = new File(outputsDir, 'outputs.db')
        
        // Migration detection: SQLite DB absent but .properties files exist
        boolean needsMigration = !dbFile.exists() && propertyFilesExist(outputsDir)
        
        SQLiteOutputMetaDataStore store = new SQLiteOutputMetaDataStore()
        
        if(needsMigration) {
            migrate(store)
        }
        
        return store
    }
    
    /**
     * Determine which backend to use by reading config.
     * <p>
     * Order of precedence:
     * <ol>
     *   <li>{@code outputMetaData.backend} in user config</li>
     *   <li>{@code bpipe.config} default block values</li>
     *   <li>{@code "sqlite"} (hard default)</li>
     * </ol>
     */
    @groovy.transform.CompileStatic(groovy.transform.TypeCheckingMode.SKIP)
    private static String determineBackend() {
        def outputMetaDataConfig = Config.userConfig?.get('outputMetaData')
        if(outputMetaDataConfig instanceof Map || outputMetaDataConfig instanceof groovy.util.ConfigObject) {
            String backend = outputMetaDataConfig.backend
            if(backend in ['sqlite', 'properties']) {
                return backend
            }
        }
        return 'sqlite'
    }
    
    /**
     * Check whether the outputs directory contains any legacy property files
     * (excluding known non-metadata files like the serialized graph cache).
     */
    private static boolean propertyFilesExist(File outputsDir) {
        if(!outputsDir.exists())
            return false
            
        return outputsDir.listFiles().any { File f ->
            PropertyFileOutputMetaDataStore.isOutputMetaFile(f)
        }
    }
    
    /**
     * Migrate legacy property files into the SQLite database.
     * <p>
     * This is a one-time operation: all existing metadata is read from
     * property files and bulk-inserted into SQLite. The property files
     * are left in place as a backup.
     */
    private static void migrate(SQLiteOutputMetaDataStore store) {
        log.info "Migrating legacy property files to SQLite"
        
        PropertyFileOutputMetaDataStore legacyStore = new PropertyFileOutputMetaDataStore()
        List<OutputMetaData> all = legacyStore.loadAll()
        
        log.info "Migrating ${all.size()} output metadata entries to SQLite"
        store.saveAll(all)
        store.flush()
        
        log.info "Migration complete: ${all.size()} entries written to SQLite"
    }
}