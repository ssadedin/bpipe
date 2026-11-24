package bpipe

import static org.junit.Assert.*

import java.nio.file.Files
import java.nio.file.Path

import org.junit.Before
import org.junit.Test

import bpipe.storage.LocalFileSystemStorageLayer

class PipelineFileTest {

    Path dir

    LocalFileSystemStorageLayer local = new LocalFileSystemStorageLayer()

    @Before
    void setUp() {
        dir = Files.createTempDirectory('pipelinefiletest')
        dir.toFile().deleteOnExit()
    }

    PipelineFile pf(String name) {
        new PipelineFile(dir.resolve(name).toString(), local)
    }

    @Test
    void 'existing files are not missing'() {
        dir.resolve('a.txt').toFile().text = 'hello'
        assert PipelineFile.flushAndFindMissing([pf('a.txt')], 1000).isEmpty()
        assert !pf('a.txt').flushMetadataAndCheckIfMissing(1000)
        assert pf('a.txt').existsWithoutFlush()
    }

    @Test
    void 'missing files are reported in the order given'() {
        dir.resolve('b.txt').toFile().text = 'hello'
        def files = [pf('c.txt'), pf('b.txt'), pf('a.txt')]
        assert PipelineFile.flushAndFindMissing(files, 0)*.path == [pf('c.txt').path, pf('a.txt').path]
    }

    @Test
    void 'a file that appears while waiting is found'() {
        Thread.start {
            Thread.sleep(300)
            dir.resolve('late.txt').toFile().text = 'hello'
        }
        assert !pf('late.txt').flushMetadataAndCheckIfMissing(1000)
    }

    @Test
    void 'many missing files cost one timeout, not one per file'() {

        List<PipelineFile> files = (1..20).collect { pf("missing_${it}.txt") }

        long start = System.currentTimeMillis()
        List<PipelineFile> missing = PipelineFile.flushAndFindMissing(files, 500)
        long elapsed = System.currentTimeMillis() - start

        assert missing.size() == 20

        // One wait of 2 x 400ms; checking one at a time would have taken 20 x 800ms
        assert elapsed >= 800
        assert elapsed < 4000 : "Batch check of 20 missing files took ${elapsed}ms"
    }

    @Test
    void 'files in a directory that does not exist are missing'() {
        PipelineFile f = new PipelineFile(dir.resolve('nodir/x.txt').toString(), local)
        assert PipelineFile.flushAndFindMissing([f], 0) == [f]
    }
}
