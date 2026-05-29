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

/**
 * Abstraction over the persistence backend for {@link OutputMetaData}.
 * <p>
 * The default implementation ({@link PropertyFileOutputMetaDataStore}) writes one
 * Java .properties file per output into <code>.bpipe/outputs/</code>.  The planned
 * successor ({@code SQLiteOutputMetaDataStore}) consolidates all records into a
 * single SQLite database file to eliminate the per-file inode cost that becomes a
 * bottleneck for large-scale pipelines.
 */
interface OutputMetaDataStore {

    /**
     * Persist metadata for a single output file. If a record already exists for
     * the same output it should be replaced.
     */
    void save(OutputMetaData p)

    /**
     * Load all output metadata records from the backing store and return them
     * sorted by timestamp (ascending).
     */
    List<OutputMetaData> loadAll()

    /**
     * Return true if the backing store already contains a record for the given output.
     */
    boolean exists(OutputMetaData p)

    /**
     * Flush any buffered writes to durable storage. Must be called before the
     * process exits to ensure all enqueued records are persisted.
     * Implementations with fully synchronous write paths may treat this as a no-op.
     */
    void flush()
}
