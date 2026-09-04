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
 * The schema uses a flat {@code outputs} table matching the fields of
 * {@link OutputMetaData}, plus {@code output_inputs} (input→output edges)
 * and {@code output_parents} (pre-computed parent-child edges in the DAG)
 * for efficient ancestry traversal.
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
        
        // Input edges: which inputs each output consumes
        db.execute("""
            CREATE TABLE IF NOT EXISTS output_inputs (
                output_canonical_path TEXT NOT NULL,
                input_canonical_path  TEXT NOT NULL,
                PRIMARY KEY (output_canonical_path, input_canonical_path)
            )
        """)
        db.execute("CREATE INDEX IF NOT EXISTS idx_oi_input ON output_inputs(input_canonical_path)")
        
        // Pre-computed parent edges: which inputs are themselves outputs of other stages
        db.execute("""
            CREATE TABLE IF NOT EXISTS output_parents (
                output_canonical_path  TEXT NOT NULL,
                parent_canonical_path  TEXT NOT NULL,
                PRIMARY KEY (output_canonical_path, parent_canonical_path)
            )
        """)
        db.execute("CREATE INDEX IF NOT EXISTS idx_op_parent ON output_parents(parent_canonical_path)")
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
     * Insert or replace a batch of metadata entries in a single transaction,
     * also updating the input and parent edge tables.
     */
    private void insertBatch(List<OutputMetaData> batch) {
        db.withTransaction {
            // 1. Insert/replace main output rows
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
            
            // 2. Update input edges: delete old, insert new
            for(OutputMetaData p in batch) {
                String outputPath = p.canonicalPath ?: p.outputPath
                db.execute("DELETE FROM output_inputs WHERE output_canonical_path = ?", outputPath)
                db.execute("DELETE FROM output_parents WHERE output_canonical_path = ?", outputPath)
            }
            
            db.withBatch("""
                INSERT INTO output_inputs (output_canonical_path, input_canonical_path)
                VALUES (?, ?)
            """) { stmt ->
                for(OutputMetaData p in batch) {
                    String outputPath = p.canonicalPath ?: p.outputPath
                    for(inp in p.inputs ?: []) {
                        stmt.addBatch(outputPath, Utils.canonicalFileFor(inp).path)
                    }
                }
            }
            
            // 3. Build parent edges: for each output, its inputs that exist as other outputs
            // become parent-child edges in the DAG
            db.withBatch("""
                INSERT OR REPLACE INTO output_parents (output_canonical_path, parent_canonical_path)
                VALUES (?, ?)
            """) { stmt ->
                for(OutputMetaData p in batch) {
                    String outputPath = p.canonicalPath ?: p.outputPath
                    for(inp in p.inputs ?: []) {
                        String inputPath = Utils.canonicalFileFor(inp).path
                        def row = db.firstRow("SELECT 1 FROM outputs WHERE canonicalPath = ?", inputPath)
                        if(row) {
                            stmt.addBatch(outputPath, inputPath)
                        }
                    }
                }
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
    List<OutputMetaData> loadAncestryChain(String canonicalPath) {
        List<OutputMetaData> result = []
        
        // Walk chain: find the target, then recursively find parents
        String currentPath = canonicalPath
        while(currentPath != null) {
            def row = db.firstRow("SELECT * FROM outputs WHERE canonicalPath = ?", currentPath)
            if(row == null) {
                // Also try outputPath as fallback
                row = db.firstRow("SELECT * FROM outputs WHERE outputPath = ?", currentPath)
            }
            if(row == null)
                break
            
            OutputMetaData omd = rowToOutputMetaData(row)
            result.add(0, omd) // prepend so result is root→target
            
            // Walk up to find the parent that is itself an output
            // The parent is the first input that exists in the outputs table
            currentPath = null
            for(inp in omd.inputs) {
                String inputPath = Utils.canonicalFileFor(inp).path
                // Check output_parents table
                def opRow = db.firstRow(
                    "SELECT parent_canonical_path FROM output_parents WHERE output_canonical_path = ? AND parent_canonical_path = ?",
                    omd.canonicalPath, inputPath)
                if(opRow != null) {
                    currentPath = inputPath
                    break
                }
                // Fallback: direct check
                def checkRow = db.firstRow("SELECT 1 FROM outputs WHERE canonicalPath = ?", inputPath)
                if(checkRow != null) {
                    currentPath = inputPath
                    break
                }
            }
        }
        
        return result
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