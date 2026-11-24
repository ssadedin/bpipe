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
package bpipe.storage

import java.nio.file.DirectoryStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Function

import groovy.transform.CompileStatic
import groovy.util.logging.Log

/**
 * Performs the directory listings that Bpipe uses to force a non-coherent file system
 * client (eg: NFS, with its attribute and negative dentry caches) to refresh its view
 * of a directory before concluding that a file in it does not exist.
 * <p>
 * Listing a large directory on a network file system is expensive, and in a highly
 * concurrent pipeline many threads tend to check for files in the <i>same</i> directory
 * at the same time. Previously each of them performed its own listing, so N threads
 * produced N simultaneous listings of the same (possibly very large) directory, all
 * contending with each other.
 * <p>
 * This class coalesces those listings per directory without weakening the guarantee
 * that callers rely on. That guarantee is:
 * <blockquote>
 *   when {@link #flush(Path)} returns, a complete listing of the directory has been
 *   performed that <b>started after</b> <code>flush</code> was called.
 * </blockquote>
 * This is exactly what a caller would get by listing the directory itself, so a caller
 * that observed a file to be missing, then calls <code>flush</code>, then checks the file
 * again, gets the same protection as before. What is shared is only a listing that a
 * caller would otherwise have had to wait for anyway: a listing that started <i>before</i>
 * the caller asked is never reused, because it may predate whatever the caller is trying
 * to see. At most one listing per directory is in progress at any time; callers that
 * arrive while one is running wait for it, and if it started too early to satisfy them,
 * one of them performs a fresh listing that all of them then share.
 * <p>
 * Timing uses {@link System#nanoTime()}, which is monotonic and comparable across
 * threads within the JVM. Comparisons are strict, so a listing that started in the same
 * nanosecond as a request is (conservatively) not considered to satisfy it.
 *
 * @author simon.sadedin
 */
@Log
@CompileStatic
class DirectoryFlusher {

    /**
     * The flusher used by Bpipe at runtime. Tests construct their own instances.
     */
    static final DirectoryFlusher instance = new DirectoryFlusher()

    private static class DirectoryState {

        /**
         * True while some thread is performing a listing of the directory
         */
        boolean listingInProgress = false

        /**
         * True once at least one listing has completed (successfully or not)
         */
        boolean hasCompleted = false

        /**
         * The {@link System#nanoTime()} at which the most recently started <i>completed</i>
         * listing began. Only meaningful when {@link #hasCompleted} is true.
         */
        long lastCompletedStartNanos = 0L
    }

    private final ConcurrentHashMap<Path, DirectoryState> states = new ConcurrentHashMap<>()

    /**
     * Number of listings actually performed: useful for diagnostics and tests
     */
    final AtomicLong listingCount = new AtomicLong()

    /**
     * Ensure that a full listing of <code>dir</code> that started after this method was
     * called has completed. See class documentation for the precise guarantee.
     * <p>
     * Failure to list the directory (eg: because it does not exist) is logged and
     * otherwise ignored, matching the previous behaviour of the individual listings
     * this replaces.
     *
     * @param dir   directory to flush; if null, nothing is done
     */
    void flush(final Path dir) {

        if(dir == null)
            return

        final long requestedNanos = System.nanoTime()

        final DirectoryState state = states.computeIfAbsent(dir, { Path p -> new DirectoryState() } as Function<Path,DirectoryState>)

        synchronized(state) {
            while(true) {
                // A listing that began after we asked is as good as one we perform ourselves
                if(state.hasCompleted && (state.lastCompletedStartNanos - requestedNanos > 0L))
                    return

                if(!state.listingInProgress)
                    break

                // Someone else is listing: wait for them, then re-evaluate whether their
                // listing started late enough to satisfy us
                state.wait()
            }
            state.listingInProgress = true
        }

        // Taken after the request and before the listing begins, so it is a true lower
        // bound on when the listing started
        final long startNanos = System.nanoTime()
        try {
            listDirectory(dir)
        }
        finally {
            synchronized(state) {
                state.listingInProgress = false
                if(!state.hasCompleted || (startNanos - state.lastCompletedStartNanos > 0L))
                    state.lastCompletedStartNanos = startNanos
                state.hasCompleted = true
                state.notifyAll()
            }
        }
    }

    /**
     * Convenience: flush the directory that contains the given file, tolerating
     * any problem in computing the parent directory.
     */
    void flushParentOf(final File file) {
        Path parent = null
        try {
            parent = file.absoluteFile.parentFile?.toPath()
        }
        catch(Exception e) {
            log.warning("Unable to determine parent directory of $file to flush: $e")
        }
        flush(parent)
    }

    /**
     * Perform one listing of the directory.
     * <p>
     * For the local (default) file system, which includes network file systems mounted on
     * this host, the entries are all read (so that the file system has to produce all of
     * them) but not retained. This matches the full listing that
     * {@link LocalFileSystemStorageLayer#exists} has always done.
     * <p>
     * For other file system providers (eg: the S3 and Google Cloud storage layers, whose
     * paths address an object store) the directory is only opened, as was previously done
     * for them: reading every entry there would mean paging through every object under
     * the prefix.
     */
    protected void listDirectory(final Path dir) {
        listingCount.incrementAndGet()
        DirectoryStream<Path> stream = null
        try {
            stream = Files.newDirectoryStream(dir)
            if(dir.fileSystem.is(FileSystems.default)) {
                Iterator<Path> i = stream.iterator()
                while(i.hasNext())
                    i.next()
            }
        }
        catch(NoSuchFileException e) {
            log.info("Directory $dir to flush does not exist")
        }
        catch(Exception e) {
            log.warning("Failed to list files of directory $dir to flush file system: $e")
        }
        finally {
            if(stream != null) {
                try { stream.close() } catch(Exception e) { /* nothing useful to do */ }
            }
        }
    }
}
