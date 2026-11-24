package bpipe

import bpipe.storage.DirectoryFlusher
import bpipe.storage.LocalFileSystemStorageLayer
import bpipe.storage.StorageLayer
import bpipe.storage.UnknownStoragePipelineFile
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Log

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.regex.Pattern

/**
 * A class that abstracts the type of underlying storage that a file is stored in,
 * while presenting a common API.
 * <p>
 * Note: to some extent this mirrors the purpose of the {@link java.nio.file.Path} class.
 * Unfortunately, that class is not serializable, and presents a very differnet API to
 * {@link java.io.File}. This class offers a serializable wrapper that unifies the APIs 
 * a little bit more.
 * 
 * @author simon.sadedin
 */
@Log
@CompileStatic
class PipelineFile implements Serializable {
   
    public static final long serialVersionUID = 0L
    
    String path
    
    StorageLayer storage
    
	/**
	 * Legacy : string
	 * Current versions : Branch
	 */
    private def sourceBranch
    
    protected PipelineFile(String path) {
        assert path != null
        this.path = path
    }
    
    PipelineFile(String path, StorageLayer storage) {
        assert path != null
        assert storage != null
        this.path = path
        this.storage = storage
    }
    
    PipelineFile(String path, StorageLayer storage, Branch sourceBranch) {
        this(path, storage)
        this.sourceBranch = sourceBranch
    }

    PipelineFile(String path, StorageLayer storage, String channel) {
        this(path, storage)
        this.sourceBranch = new Branch(name:channel)
    }
    
    
    PipelineFile newName(String newName) {
        return new PipelineFile(newName, storage, (Branch)sourceBranch)
    }
    
    boolean exists() {
        storage.exists(path)
    }
    
//    @Memoized
    Path toPath() {
        storage.toPath(path)
    }
    
//    @Memoized
    String getName() {
        toPath().fileName
    }
    
    PipelineFile normalize() {
        new PipelineFile(toPath().normalize().toString(), storage, (Branch)sourceBranch)
    }
    
    PipelineFile div(String subDir) {
        new PipelineFile(this.path + '/' + subDir, storage, (Branch)sourceBranch)
    }
    
    boolean isDirectory() {
        Files.isDirectory(toPath())
    }
    
    long lastModified() {
       try {
           Files.getLastModifiedTime(toPath()).toMillis() 
       }
       catch(NoSuchFileException ex) {
           return 0
       }
    }
    
    long length() {
        if(exists())
            return Files.size(toPath())
        else
            return 0L
    }
    
    String getAbsolutePath() {
        toPath().toAbsolutePath().toString()
    }
    
    /**
     * @param pattern   compiled regex pattern
     * @return  true iff the name (not whole path) of this file matches the given pattern
     */
    boolean matches(Pattern pattern) {
        getName().matches(pattern)
    }
    
    String getPrefix() {
        PipelineCategory.getPrefix(this.toString())
    }
    
    /**
     * Check whether the file exists without taking any measures to flush stale file
     * system caches.
     * <p>
     * <b>Note:</b> subclasses that override {@link #exists()} must override this too,
     * otherwise bulk checks (which use this after flushing directories themselves) will
     * not see their logic.
     */
    boolean existsWithoutFlush() {
        storage.existsWithoutFlush(path)
    }

    /**
     * The interval between successive re-checks of a file that is missing while
     * waiting for it to appear (see {@link #flushAndFindMissing})
     */
    static final long SYNC_POLL_INTERVAL_MS = 400L

    /**
     * @return the configured time to wait for files that appear to be missing to
     *         appear (<code>fileSystemSyncTimeMs</code>), default 500ms
     */
    static long getFileSystemSyncTimeMs() {
        Config.userConfig.getOrDefault('fileSystemSyncTimeMs', 500) as Long
    }

    /**
     * Determine whether this file is missing in a way that cannot be accounted for
     * by the given output meta data, flushing the file system and waiting up to
     * <code>fileSystemSyncTimeMs</code> for it to appear if it initially appears missing.
     * <p>
     * To check many files, prefer {@link #isMissingWithoutFlush} followed by a single call to
     * {@link #flushAndFindMissing} for all the candidates, which shares the flushes and
     * waiting between them.
     */
    boolean isMissing(OutputMetaData p, String type) {
        if(!isMissingWithoutFlush(p, type))
            return false
        return !flushAndFindMissing([this], fileSystemSyncTimeMs).isEmpty()
    }

    /**
     * First stage of the missing file check: return false if the file is visible right now
     * or if it is absent but the meta data says it was cleaned up (in which case its absence
     * is expected); otherwise return true, meaning the file is a candidate to be flushed and
     * waited for via {@link #flushAndFindMissing}.
     * <p>
     * No flush is performed for cleaned up files because the result would be "not missing"
     * whether or not the flush revealed them.
     */
    boolean isMissingWithoutFlush(OutputMetaData p, String type) {

        if(this.existsWithoutFlush())
            return false // not missing

        if(!p) {
            log.info "There are no properties for $path and file is missing"
            return true
        }

        if(p.cleaned) {
            log.info "File $path [$type] does not exist but has a properties file indicating it was cleaned up"
            return false
        }
        else {
            log.info "File $path [$type] does not exist, has a properties file indicating it is not cleaned up"
            return true
        }
    }

    /**
     * Flush the parent directory of this file and check if it is (still) missing, then
     * keep re-checking at intervals for up to <code>timeout</code> ms.
     *
     * @return true if the file is missing after all of that
     */
    boolean flushMetadataAndCheckIfMissing(long timeout=0L) {
        return !flushAndFindMissing([this], timeout).isEmpty()
    }

    /**
     * Find which of the given files are really missing, allowing for non-coherent
     * file systems (eg: NFS) whose clients may have a stale view of a directory, and for
     * files that are moments away from becoming visible (eg: a remote job reported
     * complete slightly before its outputs are visible here).
     * <p>
     * For each file, the guarantee is the same as was previously given by checking files
     * one at a time:
     * <ol>
     *   <li>the parent directory is listed (in full) and then the file is checked again;
     *   <li>if it is still missing, it is re-checked every {@link #SYNC_POLL_INTERVAL_MS}
     *       ms, each time preceded by a fresh listing of its directory, for at least
     *       <code>timeoutMs</code> after it was first checked.
     * </ol>
     * The difference is that this is done for all of the files together: each round lists
     * each distinct directory once for all of the files in it (and shares listings with
     * other threads, see {@link DirectoryFlusher}), and the waiting happens once for
     * the whole set rather than once per file. Checking N missing files therefore costs one
     * timeout rather than N timeouts.
     *
     * @param files     files that have been observed to be missing
     * @param timeoutMs how long to keep re-checking files that remain missing
     * @return the files that are still missing, in the order given
     */
    static List<PipelineFile> flushAndFindMissing(final Collection<PipelineFile> files, final long timeoutMs) {

        List<PipelineFile> missing = flushAndRecheck(files)

        long remaining = timeoutMs
        while(!missing.isEmpty() && remaining > 0) {
            Thread.sleep(SYNC_POLL_INTERVAL_MS)
            missing = flushAndRecheck(missing)
            remaining -= SYNC_POLL_INTERVAL_MS
        }

        if(!missing.isEmpty()) {
            log.info "${missing.size()} file(s) missing after flushing directories and waiting ${timeoutMs}ms: " +
                     missing.take(10).join(', ') + (missing.size() > 10 ? ', ...' : '')
        }
        return missing
    }

    /**
     * One round of {@link #flushAndFindMissing}: for each distinct parent directory, flush it
     * then check the files in it.
     *
     * @return the files still missing, in the order given
     */
    private static List<PipelineFile> flushAndRecheck(final Collection<PipelineFile> files) {

        // Group by directory so that each directory is listed once per round
        // (LinkedHashMap: deterministic order, and permits a null key)
        final Map<Path, List<PipelineFile>> byDirectory = new LinkedHashMap<Path, List<PipelineFile>>()
        for(PipelineFile f in files) {
            Path dir = f.parentDirectoryForFlush()
            List<PipelineFile> dirFiles = byDirectory[dir]
            if(dirFiles == null) {
                dirFiles = new ArrayList<PipelineFile>()
                byDirectory[dir] = dirFiles
            }
            dirFiles.add(f)
        }

        // Identity based, so that the result is exactly the input objects that were not found 
        // (PipelineFile.equals compares only the path)
        final Set<PipelineFile> stillMissing = Collections.newSetFromMap(new IdentityHashMap<PipelineFile,Boolean>())
        byDirectory.each { Path dir, List<PipelineFile> dirFiles ->

            log.info "Listing directory $dir to flush file system for ${dirFiles.size()} file(s) that do not appear to exist"

            DirectoryFlusher.instance.flush(dir)

            for(PipelineFile f in dirFiles) {
                if(f.existsWithoutFlush())
                    log.info("File " + f + " revealed by listing directory to flush metadata")
                else
                    stillMissing.add(f)
            }
        }

        return files.grep { stillMissing.contains(it) } as List<PipelineFile>
    }

    /**
     * @return the directory to list in order to flush the file system's view of this file,
     *         or null if it cannot be determined
     */
    private Path parentDirectoryForFlush() {
        try {
            return this.toPath().toAbsolutePath().parent
        }
        catch(Exception e) {
            log.warning("Unable to determine parent directory of " + this + " to flush file system: " + e)
            return null
        }
    }
   
    @Override
    boolean equals(Object o) {
        if(this.is(o))
            return true
        if(!(o instanceof PipelineFile))
            return false
        PipelineFile other = (PipelineFile)o
        return this.path == other.path
    }
    
    @Override
    int hashCode() {
        return path.hashCode()
    }

    @Override
    String toString() {
        path
    }
    
    String renderToCommand() {
        assert storage.name != 'unknown'
        
        if(storage instanceof LocalFileSystemStorageLayer) 
            return this.toString()
        else
            return "{bpipe:$storage.name://${this.toString()}}"
    }
	
	private void readObject(ObjectInputStream aInputStream) throws ClassNotFoundException, IOException {
		aInputStream.defaultReadObject()

		if(this.@sourceBranch instanceof String) {
			log.info "Converting legacy source branch to string form"
			this.@sourceBranch = new Branch(name: (String)this.@sourceBranch)
		}
	}

	
	@CompileStatic
	Branch getSourceBranch() {
		return (Branch)this.@sourceBranch
	}
	
	@CompileStatic
	void setSourceBranch(def value) {
		this.@sourceBranch = (Branch)value
	}
	
}

@CompileStatic
class PipelineFilePathComparator implements Comparator {

    @Override
    public int compare(Object o1, Object o2) {
        return ((PipelineFile)o1).path.compareTo(((PipelineFile)o2).path)
    }
    
}
