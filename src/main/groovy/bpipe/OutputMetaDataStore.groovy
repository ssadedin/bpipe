package bpipe

import groovy.transform.CompileStatic

/**
 * Abstraction over the persistence layer for output metadata.
 * <p>
 * Currently two implementations exist:
 * <ul>
 *   <li>{@link PropertyFileOutputMetaDataStore} — the legacy per-file properties format</li>
 *   <li>{@link SQLiteOutputMetaDataStore} — a single SQLite database (future)</li>
 * </ul>
 * 
 * @see Dependencies#store
 * @see OutputMetaDataStoreFactory
 */
@CompileStatic
interface OutputMetaDataStore {
    
    /**
     * Persist the given metadata entry (create or update).
     */
    void save(OutputMetaData p)
    
    /**
     * Persist multiple metadata entries in a single batch operation.
     * <p>
     * For the property-file backend this calls {@link #save(OutputMetaData)} in a loop.
     * For the SQLite backend this performs a bulk insert bypassing the async queue.
     */
    void saveAll(List<OutputMetaData> items)
    
    /**
     * Update an existing metadata entry (e.g. preserve or cleaned status changes).
     * The default implementation delegates to {@link #save(OutputMetaData)}.
     */
    void update(OutputMetaData p)
    
    /**
     * Load metadata for a previously persisted output into the supplied object.
     * <p>
     * After this call the fields of {@code p} are populated from the stored data.
     */
    void load(OutputMetaData p)
    
    /**
     * Load all persisted metadata entries.
     * @return list of {@link OutputMetaData} objects, sorted by timestamp ascending
     */
    List<OutputMetaData> loadAll()
    
    /**
     * Return {@code true} if metadata exists for the given output.
     */
    boolean exists(OutputMetaData p)
    
    /**
     * Flush any buffered writes to the underlying storage.
     * <p>
     * Called at pipeline completion to ensure all metadata has been persisted.
     * For property files this is a no-op; for SQLite this drains the async write queue.
     */
    void flush()
    
    /**
     * Release any resources held by this store (connections, threads, etc.).
     */
    void close()
}