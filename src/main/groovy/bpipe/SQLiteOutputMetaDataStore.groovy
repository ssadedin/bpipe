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

import groovy.sql.Sql
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import bpipe.storage.StorageLayer

/**
 * A {@link OutputMetaDataStore} implementation that persists output metadata
 * to a single SQLite database at {@code .bpipe/outputs/outputs.db}.
 * <p>
 * Writes are enqueued on a {@link LinkedBlockingQueue} and flushed to the
 * database asynchronously by a background thread, so that high-concurrency
 * pipeline stages do not block on database I/O.  At pipeline completion,
 * {@link #flush()} drains the queue synchronously.
 * <p>
 * The schema is a single flat table matching the fields of {@link OutputMetaData}.
 * 
 * @see OutputMetaDataStore
 * @see OutputMetaDataStoreFactory
 */
@Log
@CompileStatic(TypeCheckingMode.SKIP)
class SQLiteOutputMetaDataStore implements OutputMetaDataStore {
    
    /**
     * SQLite JDBC connection, opened once at construction and closed in {@link #close()}
     */
    Sql db
    
    /**
     * Thread-safe queue of metadata entries awaiting database write
     */
    LinkedBlockingQueue<OutputMetaData> writeQueue = new LinkedBlockingQueue<>()
    
    /**
     * Background daemon thread that drains the write queue
     */
    Thread flushThread
    
    /**
     * Interval in ms between flush thread wake-ups
     */
    int flushIntervalMs = 200
    
    /**
     * Maximum number of entries to drain per batch
     */
    int batchSize = 100
    
    /**
     * Flag controlling the flush thread's main loop
     */
    volatile boolean running = true
    
    /**
     * Path to the SQLite database file
     */
    String dbPath
    
    SQLiteOutputMetaDataStore() {
        init()
    }
    
    /**
     * Initialise the store: create the directory, open the connection,
     * create the schema, and start the background flush thread.
     */
    private void init() {
        flushIntervalMs = (int)(Config.userConfig?.getOrDefault('outputMetaData', [:])?.flushIntervalMs ?: 200)
        batchSize = (int)(Config.userConfig?.getOrDefault('outputMetaData', [:])?.batchSize ?: 100)
        
        File dbDir = new File(OutputMetaData.OUTPUT_METADATA_DIR)
        if(!dbDir.exists()) {
            dbDir.mkdirs()
        }
        
        dbPath = new File(dbDir, "outputs.db").absolutePath
        
        log.info "Opening SQLite metadata database at $dbPath"
        db = Sql.newInstance("jdbc:sqlite:$dbPath", "org.sqlite.JDBC")
        
        initSchema()
        startFlushThread()
    }
    
    /**
     * Create the outputs table and its indices if they do not already exist.
     */
    private void initSchema() {
        log.info "Initialising SQLite schema for output metadata"
        db.execute("""
            CREATE TABLE IF NOT EXISTS outputs (
                canonicalPath   TEXT PRIMARY KEY,
                outputPath      TEXT,
                stageName       TEXT,
                stageId         TEXT,
                commandId       TEXT,
                branchPath      TEXT,
                inputs          TEXT,
                command         TEXT,
                tools           TEXT,
                fingerprint     TEXT,
                timestamp       INTEGER,
                startTimeMs     INTEGER,
                createTimeMs    INTEGER,
                stopTimeMs      INTEGER,
                preserve        INTEGER DEFAULT 0,
                intermediate    INTEGER DEFAULT 0,
                cleaned         INTEGER DEFAULT 0,
                stub            INTEGER DEFAULT 0,
                accompanies     TEXT,
                storage         TEXT,
                basePath        TEXT
            )
        """)
        db.execute("CREATE INDEX IF NOT EXISTS idx_outputs_commandId ON outputs(commandId)")
        db.execute("CREATE INDEX IF NOT EXISTS idx_outputs_stageName ON outputs(stageName)")
        db.execute("CREATE INDEX IF NOT EXISTS idx_outputs_outputPath ON outputs(outputPath)")
    }
    
    /**
     * Start the background flush daemon thread
     */
    private void startFlushThread() {
        flushThread = new Thread({
            while(running) {
                try {
                    Thread.sleep(flushIntervalMs)
                    flushPending()
                }
                catch(InterruptedException e) {
                    Thread.currentThread().interrupt()
                    break
                }
                catch(Exception e) {
                    log.warning "Error in SQLite flush thread: ${e.message}"
                }
            }
            // Final drain before thread exits
            flushPending()
        }, "bpipe-sqlite-flush")
        flushThread.daemon = true
        flushThread.start()
        log.info "Started SQLite flush thread (interval=${flushIntervalMs}ms, batchSize=${batchSize})"
    }
    
    /**
     * Drain pending entries from the queue and write them to the database
     * in a single batch transaction.
     */
    private void flushPending() {
        List<OutputMetaData> batch = []
        writeQueue.drainTo(batch, batchSize)
        if(batch.isEmpty())
            return
        
        log.info "Flushing ${batch.size()} output metadata entries to SQLite"
        insertBatch(batch)
    }
    
    /**
     * Insert or replace a batch of metadata entries in a single transaction.
     */
    private void insertBatch(List<OutputMetaData> batch) {
        db.withBatch("""
            INSERT OR REPLACE INTO outputs (
                canonicalPath, outputPath, stageName, stageId, commandId,
                branchPath, inputs, command, tools, fingerprint,
                timestamp, startTimeMs, createTimeMs, stopTimeMs,
                preserve, intermediate, cleaned, stub,
                accompanies, storage, basePath
            ) VALUES (
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?
            )
        """) { stmt ->
            for(OutputMetaData p in batch) {
                stmt.addBatch(
                    p.canonicalPath ?: p.outputPath,
                    p.outputPath,
                    p.stageName,
                    p.stageId,
                    p.commandId,
                    p.branchPath ?: '',
                    p.inputs ? p.inputs.join(',') : '',
                    p.command,
                    p.tools ?: '',
                    p.fingerprint,
                    p.timestamp ?: 0L,
                    p.startTimeMs ?: 0L,
                    p.createTimeMs ?: 0L,
                    p.stopTimeMs ?: 0L,
                    p.preserve ? 1 : 0,
                    p.intermediate ? 1 : 0,
                    p.cleaned ? 1 : 0,
                    p.stub ? 1 : 0,
                    p.accompanies,
                    p.storage ? p.storage.name : 'local',
                    p.basePath
                )
            }
        }
    }
    
    // ===== OutputMetaDataStore interface =====
    
    @Override
    void save(OutputMetaData p) {
        writeQueue.offer(p)
    }
    
    @Override
    void saveAll(List<OutputMetaData> items) {
        insertBatch(items)
    }
    
    @Override
    void update(OutputMetaData p) {
        writeQueue.offer(p)
    }
    
    @Override
    void load(OutputMetaData p) {
        // No-op: INSERT OR REPLACE overwrites all columns,
        // so there is no need to pre-read existing values.
    }
    
    @Override
    List<OutputMetaData> loadAll() {
        log.info "Loading all output metadata from SQLite"
        List<OutputMetaData> result = []
        List<groovy.sql.GroovyRowResult> rows = db.rows("SELECT * FROM outputs ORDER BY timestamp ASC")
        for(row in rows) {
            result.add(rowToOutputMetaData(row))
        }
        log.info "Loaded ${result.size()} output metadata entries from SQLite"
        return result
    }
    
    @Override
    boolean exists(OutputMetaData p) {
        // Check pending queue first
        for(OutputMetaData pending in writeQueue) {
            if(pending.outputPath == p.outputPath) {
                return true
            }
        }
        // Then check database
        def row = db.firstRow("SELECT 1 FROM outputs WHERE outputPath = ?", p.outputPath)
        return row != null
    }
    
    @Override
    void flush() {
        log.info "Synchronously flushing SQLite write queue"
        // Drain the queue directly (synchronous write)
        List<OutputMetaData> remainder = []
        writeQueue.drainTo(remainder)
        if(!remainder.isEmpty()) {
            insertBatch(remainder)
        }
    }
    
    @Override
    void close() {
        log.info "Closing SQLite metadata store"
        running = false
        flushThread.interrupt()
        try {
            flushThread.join(5000)
        }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt()
        }
        // Final drain
        flush()
        db?.close()
        db = null
        log.info "SQLite metadata store closed"
    }
    
    /**
     * Convert a SQLite result row to an OutputMetaData object.
     */
    private OutputMetaData rowToOutputMetaData(row) {
        OutputMetaData p = new OutputMetaData()
        p.outputPath = row.outputPath
        p.canonicalPath = row.canonicalPath
        p.stageName = row.stageName
        p.stageId = row.stageId
        p.commandId = row.commandId
        p.branchPath = row.branchPath ?: ''
        String inputsStr = row.inputs
        p.inputs = inputsStr ? inputsStr.split(',') as List : []
        p.command = row.command
        p.tools = row.tools ?: ''
        p.fingerprint = row.fingerprint
        p.timestamp = row.timestamp ?: 0L
        p.startTimeMs = row.startTimeMs ?: 0L
        p.createTimeMs = row.createTimeMs ?: 0L
        p.stopTimeMs = row.stopTimeMs ?: 0L
        p.preserve = row.preserve == 1
        p.intermediate = row.intermediate == 1
        p.cleaned = row.cleaned == 1
        p.stub = row.stub == 1
        p.accompanies = row.accompanies
        p.storage = StorageLayer.create(row.storage ?: 'local')
        p.basePath = row.basePath
        p.outputFile = new PipelineFile(p.outputPath, p.storage)
        return p
    }
}