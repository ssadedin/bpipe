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
import groovy.util.logging.Log

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import bpipe.storage.StorageLayer

/**
 * {@link OutputMetaDataStore} implementation backed by a SQLite database.
 * <p>
 * All output metadata records are stored in a single {@code outputs} table in
 * {@code .bpipe/outputs/outputs.db}, eliminating the per-file inode cost of the
 * legacy property-file approach.
 * <p>
 * Writes are non-blocking: callers enqueue records via {@link #save} and a
 * background daemon thread drains the queue in batches on a configurable
 * interval (default 200 ms).  {@link #flush} drains the queue synchronously
 * and is called at pipeline completion.  All database access is synchronized
 * on this instance so the background thread and any direct callers do not race.
 */
@Log
class SQLiteOutputMetaDataStore implements OutputMetaDataStore {

    static final String DB_FILENAME = '.bpipe/outputs/outputs.db'

    private static final String CREATE_TABLE_SQL = '''
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
    '''

    private static final String INSERT_SQL = '''
        INSERT OR REPLACE INTO outputs (
            canonicalPath, outputPath, stageName, stageId, commandId,
            branchPath, inputs, command, tools, fingerprint,
            timestamp, startTimeMs, createTimeMs, stopTimeMs,
            preserve, intermediate, cleaned, stub, accompanies, storage, basePath
        ) VALUES (
            :canonicalPath, :outputPath, :stageName, :stageId, :commandId,
            :branchPath, :inputs, :command, :tools, :fingerprint,
            :timestamp, :startTimeMs, :createTimeMs, :stopTimeMs,
            :preserve, :intermediate, :cleaned, :stub, :accompanies, :storage, :basePath
        )
    '''

    private final Sql db

    private final int flushIntervalMs

    private final LinkedBlockingQueue<OutputMetaData> writeQueue = new LinkedBlockingQueue<>()

    private Thread flushThread

    private volatile boolean running = true

    SQLiteOutputMetaDataStore() {
        this(DB_FILENAME, 200)
    }

    SQLiteOutputMetaDataStore(String dbPath, int flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs

        File dbFile = new File(dbPath)
        dbFile.parentFile?.mkdirs()

        db = Sql.newInstance("jdbc:sqlite:${dbFile.absolutePath}", 'org.sqlite.JDBC')
        initSchema()
        startFlushThread()
        Runtime.runtime.addShutdownHook(new Thread({ close() } as Runnable))
    }

    private void initSchema() {
        db.execute(CREATE_TABLE_SQL)
        db.execute('CREATE INDEX IF NOT EXISTS idx_commandId ON outputs(commandId)')
        db.execute('CREATE INDEX IF NOT EXISTS idx_stageName  ON outputs(stageName)')
    }

    private void startFlushThread() {
        running = true
        flushThread = new Thread({
            while(running) {
                try {
                    drainQueue()
                }
                catch(InterruptedException e) {
                    Thread.currentThread().interrupt()
                    break
                }
                catch(Exception e) {
                    log.warning("Error in output metadata flush thread: ${e.message}")
                }
            }
        } as Runnable, 'bpipe-output-metadata-flush')
        flushThread.daemon = true
        flushThread.start()
    }

    private void drainQueue() throws InterruptedException {
        OutputMetaData first = writeQueue.poll(flushIntervalMs, TimeUnit.MILLISECONDS)
        if(first == null)
            return

        List<OutputMetaData> batch = [first]
        writeQueue.drainTo(batch)
        insertBatch(batch)
    }

    private synchronized void insertBatch(List<OutputMetaData> batch) {
        db.withTransaction {
            for(OutputMetaData p in batch) {
                db.execute(INSERT_SQL, toRow(p))
            }
        }
    }

    private Map toRow(OutputMetaData p) {
        long ts = 0L
        if(p.outputFile?.exists())
            ts = p.outputFile.lastModified()
        else if(p.timestamp)
            ts = p.timestamp

        String storageName = 'local'
        if(p.outputFile?.storage)
            storageName = p.outputFile.storage.name
        else if(p.storage)
            storageName = p.storage.name

        return [
            canonicalPath: p.canonicalPath,
            outputPath:    p.outputPath,
            stageName:     p.stageName     ?: '',
            stageId:       p.stageId       ?: '',
            commandId:     p.commandId     ?: '',
            branchPath:    p.branchPath    ?: '',
            inputs:        p.inputs        ? p.inputs.join(',') : '',
            command:       p.command       ?: '',
            tools:         p.tools         ?: '',
            fingerprint:   p.fingerprint   ?: '',
            timestamp:     ts,
            startTimeMs:   p.startTimeMs,
            createTimeMs:  p.createTimeMs,
            stopTimeMs:    p.stopTimeMs,
            preserve:      p.preserve      ? 1 : 0,
            intermediate:  p.intermediate  ? 1 : 0,
            cleaned:       p.cleaned       ? 1 : 0,
            stub:          p.stub          ? 1 : 0,
            accompanies:   p.accompanies,
            storage:       storageName,
            basePath:      p.basePath      ?: Runner.runDirectory
        ]
    }

    @Override
    void save(OutputMetaData p) {
        writeQueue.offer(p)
    }

    /**
     * Bulk-insert a list of records in a single transaction.  Used by the
     * migration path and unit tests; bypasses the async queue.
     */
    void saveAll(List<OutputMetaData> records) {
        if(records)
            insertBatch(records)
    }

    @Override
    synchronized List<OutputMetaData> loadAll() {
        List<OutputMetaData> result = []
        db.eachRow('SELECT * FROM outputs') { row ->
            OutputMetaData omd = rowToMetaData(row)
            if(omd != null)
                result << omd
        }
        return result.sort { it.timestamp }
    }

    private OutputMetaData rowToMetaData(def row) {
        try {
            StorageLayer storage = StorageLayer.create(row.storage?.toString() ?: 'local')

            OutputMetaData omd = new OutputMetaData()
            omd.outputPath = row.outputPath?.toString()?.replace('\\', '/')
            omd.outputFile = new PipelineFile(omd.outputPath, storage)
            omd.storage    = storage

            if(omd.outputFile.exists())
                omd.timestamp = omd.outputFile.lastModified()
            else
                omd.timestamp = (row.timestamp ?: 0L) as long

            String storedBase = row.basePath?.toString()
            if(!storedBase || storedBase != Runner.runDirectory)
                omd.canonicalPath = Utils.canonicalFileFor(omd.outputFile.path).absolutePath
            else
                omd.canonicalPath = row.canonicalPath?.toString()

            omd.basePath     = storedBase
            omd.inputs       = row.inputs?.toString() ? row.inputs.toString().split(',').toList() : []
            omd.cleaned      = ((row.cleaned ?: 0) as int) != 0
            omd.preserve     = ((row.preserve ?: 0) as int) != 0
            omd.intermediate = ((row.intermediate ?: 0) as int) != 0
            omd.stub         = ((row.stub ?: 0) as int) != 0
            omd.commandId    = row.commandId?.toString() ?: '-1'
            omd.startTimeMs  = (row.startTimeMs  ?: 0L) as long
            omd.createTimeMs = (row.createTimeMs ?: 0L) as long
            omd.stopTimeMs   = (row.stopTimeMs   ?: 0L) as long
            omd.tools        = row.tools?.toString()       ?: ''
            omd.branchPath   = row.branchPath?.toString()  ?: ''
            omd.stageName    = row.stageName?.toString()
            omd.stageId      = row.stageId?.toString()
            omd.fingerprint  = row.fingerprint?.toString()
            omd.accompanies  = row.accompanies?.toString()
            omd.command      = row.command?.toString()

            return omd
        }
        catch(Exception e) {
            log.warning("Failed to deserialise output metadata row for '${row?.getAt('outputPath')}': ${e.message}")
            return null
        }
    }

    @Override
    synchronized boolean exists(OutputMetaData p) {
        if(!p.outputPath)
            return false
        // Also check the pending-write queue so callers get a consistent view
        // of records that have been enqueued but not yet flushed.
        if(writeQueue.any { it.outputPath == p.outputPath })
            return true
        def row = db.firstRow('SELECT COUNT(*) AS cnt FROM outputs WHERE outputPath = ?', [p.outputPath])
        return ((row?.cnt ?: 0) as int) > 0
    }

    @Override
    void load(OutputMetaData p) {
        // INSERT OR REPLACE unconditionally overwrites, so there is no need to
        // pre-read existing rows before a save.  All relevant fields are set by
        // PipelineStage via setPropertiesFromCommand() before save() is called.
    }

    @Override
    void flush() {
        List<OutputMetaData> pending = []
        writeQueue.drainTo(pending)
        if(pending)
            insertBatch(pending)
    }

    void close() {
        running = false
        flushThread?.interrupt()
        try {
            flushThread?.join(5000)
        }
        catch(InterruptedException ignore) {
            Thread.currentThread().interrupt()
        }

        // Drain anything the background thread didn't catch before it stopped.
        flush()

        try {
            db?.close()
        }
        catch(Exception e) {
            log.warning("Error closing SQLite connection: ${e.message}")
        }
    }
}
