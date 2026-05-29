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

import groovy.util.logging.Log

/**
 * Creates the appropriate {@link OutputMetaDataStore} implementation based on
 * configuration and the state of the run directory.
 * <p>
 * Configuration key: {@code outputMetaData.backend} (default {@code "sqlite"}).
 * Set to {@code "properties"} to revert to the legacy per-file behaviour with no
 * SQLite dependency.
 * <p>
 * Migration: when the SQLite database does not yet exist for a run directory that
 * contains legacy property files, those records are automatically migrated into
 * the new database in a single bulk transaction before the store is returned.
 */
@Log
class OutputMetaDataStoreFactory {

    static OutputMetaDataStore create() {
        String backend = Config.userConfig?.outputMetaData?.backend ?: 'sqlite'

        if(backend == 'properties') {
            log.info "Output metadata backend: property files (legacy mode)"
            return new PropertyFileOutputMetaDataStore()
        }

        return createSQLiteStore()
    }

    private static SQLiteOutputMetaDataStore createSQLiteStore() {
        File dbFile = new File(SQLiteOutputMetaDataStore.DB_FILENAME)
        boolean isNewDatabase = !dbFile.exists()

        int flushIntervalMs = (Config.userConfig?.outputMetaData?.flushIntervalMs ?: 200) as int
        SQLiteOutputMetaDataStore store = new SQLiteOutputMetaDataStore(
            SQLiteOutputMetaDataStore.DB_FILENAME, flushIntervalMs)

        log.info "Output metadata backend: SQLite (${dbFile.absolutePath})"

        if(isNewDatabase) {
            migrateFromPropertyFiles(store)
        }

        return store
    }

    private static void migrateFromPropertyFiles(SQLiteOutputMetaDataStore sqliteStore) {
        File outputsDir = new File(OutputMetaData.OUTPUT_METADATA_DIR)
        if(!outputsDir.exists())
            return

        boolean hasPropertyFiles = outputsDir.listFiles()
            ?.any { PropertyFileOutputMetaDataStore.isOutputMetaFile(it) }
        if(!hasPropertyFiles)
            return

        log.info "Migrating output metadata from property files to SQLite ..."
        PropertyFileOutputMetaDataStore propStore = new PropertyFileOutputMetaDataStore()
        List<OutputMetaData> existing = propStore.loadAll()
        if(!existing) {
            log.info "No records found in property files — nothing to migrate"
            return
        }

        log.info "Migrating ${existing.size()} output metadata records to SQLite"
        sqliteStore.saveAll(existing)
        log.info "Migration complete"
    }
}
