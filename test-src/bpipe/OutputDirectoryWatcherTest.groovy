
package bpipe

import java.nio.file.Path
import org.junit.Test;

class OutputDirectoryWatcherTest {
    
    @Test
    void testOutputDirectoryWatcher() {
        
        File testDir = new File("tests/odw.tmp")
        Path dir = testDir.toPath()
        dir.toFile().mkdirs()
        
        OutputDirectoryWatcher odw = new OutputDirectoryWatcher(dir)
        odw.start()
        
        long startMs = System.currentTimeMillis()
        
        Thread.sleep(1100)
        
        new File(testDir, "foo.txt").text = (new Date()).toString()
        
        println "Created file foo.txt"
        
        Thread.sleep(10000)
        
        println "Files are: " + odw.files
        
        println "File timestamp for foo.txt = " + odw.files["foo.txt"]
        
        long updateMs = odw.files["foo.txt"]
        
        println odw.timestamps.higherEntry(startMs)
        
        new File(testDir, "foo.txt").text = (new Date()).toString()
        Thread.sleep(100)
        new File(testDir, "bar.txt").text = (new Date()).toString()
        
        Thread.sleep(10000)
        
        assert odw.timestamps[updateMs] == null
        
        println "Ending timestamps: " + odw.timestamps
        
        println odw.timestamps.higherEntry(updateMs)
        
        assert "foo.txt" in odw.modifiedSince(updateMs)
        assert "bar.txt" in odw.modifiedSince(updateMs)
        
        odw.stop = true
    }
}