package bpipe.storage

import static org.junit.Assert.*

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

import org.junit.Test

class DirectoryFlusherTest {

    /**
     * A flusher whose listings are slow and are counted, and which records how many
     * were ever in progress at the same time
     */
    static class SlowFlusher extends DirectoryFlusher {

        long listingMs = 300

        AtomicInteger inProgress = new AtomicInteger()

        AtomicInteger maxInProgress = new AtomicInteger()

        CountDownLatch firstListingStarted = new CountDownLatch(1)

        List<Long> listingStartNanos = Collections.synchronizedList([])

        @Override
        protected void listDirectory(Path dir) {
            listingStartNanos << System.nanoTime()
            int n = inProgress.incrementAndGet()
            maxInProgress.accumulateAndGet(n, { a, b -> Math.max(a,b) })
            firstListingStarted.countDown()
            try {
                Thread.sleep(listingMs)
                super.listDirectory(dir)
            }
            finally {
                inProgress.decrementAndGet()
            }
        }
    }

    Path tmpDir() {
        Path dir = Files.createTempDirectory('flushtest')
        dir.toFile().deleteOnExit()
        return dir
    }

    @Test
    void 'sequential flushes never reuse an earlier listing'() {
        DirectoryFlusher flusher = new DirectoryFlusher()
        Path dir = tmpDir()
        flusher.flush(dir)
        flusher.flush(dir)
        flusher.flush(dir)
        assert flusher.listingCount.get() == 3
    }

    @Test
    void 'requests arriving during a listing share one fresh listing'() {
        SlowFlusher flusher = new SlowFlusher()
        Path dir = tmpDir()

        // First thread starts a listing
        Thread first = Thread.start { flusher.flush(dir) }
        flusher.firstListingStarted.await()

        // These all ask after that listing started, so cannot use it: they must
        // wait for it, and then share a single new one between them
        List<Long> requestNanos = Collections.synchronizedList([])
        List<Long> returnNanos = Collections.synchronizedList([])
        List<Thread> others = (1..10).collect {
            Thread.start {
                long t = System.nanoTime()
                requestNanos << t
                flusher.flush(dir)
                returnNanos << System.nanoTime()
            }
        }

        ([first] + others)*.join()

        assert flusher.listingCount.get() == 2 : "Expected exactly one extra listing for 10 concurrent requests"
        assert flusher.maxInProgress.get() == 1 : "Only one listing of a directory should run at a time"

        // Every request was satisfied by a listing that started after it was made
        long secondListingStart = flusher.listingStartNanos[1]
        requestNanos.each { assert secondListingStart > it }
    }

    @Test
    void 'different directories are listed independently'() {
        SlowFlusher flusher = new SlowFlusher()
        Path dir1 = tmpDir()
        Path dir2 = tmpDir()

        long start = System.currentTimeMillis()
        [Thread.start { flusher.flush(dir1) }, Thread.start { flusher.flush(dir2) }]*.join()

        assert flusher.listingCount.get() == 2
        assert flusher.maxInProgress.get() == 2 : "Listings of different directories should proceed concurrently"
    }

    @Test
    void 'non-existent and null directories are tolerated'() {
        DirectoryFlusher flusher = new DirectoryFlusher()
        flusher.flush(tmpDir().resolve('does_not_exist'))
        flusher.flush(null)
        flusher.flushParentOf(new File('/no/such/dir/file.txt'))
        assert flusher.listingCount.get() == 2
    }
}
